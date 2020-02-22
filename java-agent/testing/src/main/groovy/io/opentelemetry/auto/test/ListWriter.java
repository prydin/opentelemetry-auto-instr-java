package io.opentelemetry.auto.test;

import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.TreeTraverser;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListWriter implements SpanProcessor {

  private final List<List<SpanData>> traces = new ArrayList<>(); // guarded by tracesLock

  private boolean needsTraceSorting; // guarded by tracesLock
  private final Set<TraceId> needsSpanSorting = new HashSet<>(); // guarded by tracesLock

  private final Object tracesLock = new Object();

  // not using span startEpochNanos since that is not strictly increasing so can lead to ties
  private final Map<SpanId, Integer> spanOrders = new ConcurrentHashMap<>();
  private final AtomicInteger nextSpanOrder = new AtomicInteger();

  @Override
  public void onStart(final ReadableSpan readableSpan) {
    final SpanData sd = readableSpan.toSpanData();
    log.debug(
        ">>> SPAN START: {} id={} traceid={} parent={}",
        sd.getName(),
        sd.getSpanId().toLowerBase16(),
        sd.getTraceId().toLowerBase16(),
        sd.getParentSpanId().toLowerBase16());
    spanOrders.put(readableSpan.getSpanContext().getSpanId(), nextSpanOrder.getAndIncrement());
  }

  @Override
  public void onEnd(final ReadableSpan readableSpan) {
    final SpanData sd = readableSpan.toSpanData();
    log.debug(
        "<<< SPAN END: {} id={} traceid={} parent={}",
        sd.getName(),
        sd.getSpanId().toLowerBase16(),
        sd.getTraceId().toLowerBase16(),
        sd.getParentSpanId().toLowerBase16());
    final SpanData span = readableSpan.toSpanData();
    synchronized (tracesLock) {
      boolean found = false;
      for (final List<SpanData> trace : traces) {
        if (trace.get(0).getTraceId().equals(span.getTraceId())) {
          trace.add(span);
          found = true;
          break;
        }
      }
      if (!found) {
        final List<SpanData> trace = new CopyOnWriteArrayList<>();
        trace.add(span);
        traces.add(trace);
        needsTraceSorting = true;
      }
      needsSpanSorting.add(span.getTraceId());
      tracesLock.notifyAll();
    }
  }

  public void filterTraces(final Predicate<List<SpanData>> filter) {
    synchronized (tracesLock) {
      for (final Iterator<List<SpanData>> i = traces.iterator(); i.hasNext(); ) {
        if (filter.apply(i.next())) {
          i.remove();
        }
      }
    }
  }

  public List<List<SpanData>> getTraces() {
    synchronized (tracesLock) {
      // important not to sort trace or span lists in place so that any tests that are currently
      // iterating over them are not affected
      if (needsTraceSorting) {
        sortTraces();
        needsTraceSorting = false;
      }
      if (!needsSpanSorting.isEmpty()) {
        for (int i = 0; i < traces.size(); i++) {
          final List<SpanData> trace = traces.get(i);
          if (needsSpanSorting.contains(trace.get(0).getTraceId())) {
            traces.set(i, sort(trace));
          }
        }
        needsSpanSorting.clear();
      }
      // always return a copy so that future structural changes cannot cause race conditions during
      // test verification
      final List<List<SpanData>> copy = new ArrayList<>();
      for (final List<SpanData> trace : traces) {
        copy.add(new ArrayList<>(trace));
      }
      return copy;
    }
  }

  public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
    synchronized (tracesLock) {
      long remainingWaitMillis = TimeUnit.SECONDS.toMillis(20);
      while (completedTraceCount() < number && remainingWaitMillis > 0) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        tracesLock.wait(remainingWaitMillis);
        remainingWaitMillis -= stopwatch.elapsed(TimeUnit.MILLISECONDS);
      }
      final int completedTraceCount = completedTraceCount();
      if (completedTraceCount < number) {
        throw new TimeoutException(
            "Timeout waiting for "
                + number
                + " completed trace(s), found "
                + completedTraceCount
                + " completed trace(s) and "
                + traces.size()
                + " total trace(s): "
                + traces);
      }
    }
  }

  public void clear() {
    synchronized (tracesLock) {
      traces.clear();
    }
    spanOrders.clear();
  }

  @Override
  public void shutdown() {}

  // must be called under tracesLock
  private int completedTraceCount() {
    int count = 0;
    for (final List<SpanData> trace : traces) {
      if (isCompleted(trace)) {
        count++;
      }
    }
    return count;
  }

  // trace is completed if root span is present
  private boolean isCompleted(final List<SpanData> trace) {
    for (final SpanData span : trace) {
      if (!span.getParentSpanId().isValid()) {
        return true;
      }
      if (span.getParentSpanId().toLowerBase16().equals("0000000000000456")) {
        // this is a special parent id that some tests use
        return true;
      }
    }
    return false;
  }

  // must be called under tracesLock
  private void sortTraces() {
    Collections.sort(
        traces,
        new Comparator<List<SpanData>>() {
          @Override
          public int compare(final List<SpanData> trace1, final List<SpanData> trace2) {
            return Longs.compare(getMinSpanOrder(trace1), getMinSpanOrder(trace2));
          }
        });
  }

  private long getMinSpanOrder(final List<SpanData> spans) {
    long min = Long.MAX_VALUE;
    for (final SpanData span : spans) {
      min = Math.min(min, getSpanOrder(span));
    }
    return min;
  }

  private List<SpanData> sort(final List<SpanData> trace) {

    final Map<SpanId, Node> lookup = new HashMap<>();
    for (final SpanData span : trace) {
      lookup.put(span.getSpanId(), new Node(span));
    }

    for (final Node node : lookup.values()) {
      final SpanId parentSpanId = node.span.getParentSpanId();
      if (parentSpanId.isValid()) {
        final Node parentNode = lookup.get(parentSpanId);
        if (parentNode != null) {
          parentNode.childNodes.add(node);
          node.root = false;
        }
      }
    }

    final List<Node> rootNodes = new ArrayList<>();
    for (final Node node : lookup.values()) {
      sortOneLevel(node.childNodes);
      if (node.root) {
        rootNodes.add(node);
      }
    }
    sortOneLevel(rootNodes);

    final TreeTraverser<Node> traverser =
        new TreeTraverser<Node>() {
          @Override
          public Iterable<Node> children(final Node node) {
            return node.childNodes;
          }
        };

    final List<Node> orderedNodes = new ArrayList<>();
    for (final Node rootNode : rootNodes) {
      Iterables.addAll(orderedNodes, traverser.preOrderTraversal(rootNode));
    }

    final List<SpanData> orderedSpans = new ArrayList<>();
    for (final Node node : orderedNodes) {
      orderedSpans.add(node.span);
    }
    return orderedSpans;
  }

  private void sortOneLevel(final List<Node> nodes) {
    Collections.sort(
        nodes,
        new Comparator<Node>() {
          @Override
          public int compare(final Node node1, final Node node2) {
            return Ints.compare(getSpanOrder(node1.span), getSpanOrder(node2.span));
          }
        });
  }

  private int getSpanOrder(final SpanData span) {
    final Integer order = spanOrders.get(span.getSpanId());
    if (order == null) {
      throw new IllegalStateException("order not found for span: " + span);
    }
    return order;
  }

  private static class Node {

    private final SpanData span;
    private final List<Node> childNodes = new ArrayList<>();
    private boolean root = true;

    private Node(final SpanData span) {
      this.span = span;
    }
  }
}
