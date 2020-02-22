package io.opentelemetry.auto.test.asserts

import com.google.common.base.Stopwatch
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.ListWriter
import io.opentelemetry.sdk.trace.SpanData
import io.opentelemetry.trace.TraceId

import java.util.concurrent.TimeUnit

import static SpanAssert.assertSpan

class TraceAssert {
  private final List<SpanData> spans

  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(spans) {
    this.spans = spans
  }

  static void assertTrace(ListWriter writer, TraceId traceId, int expectedSize,
                          @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.TraceAssert'])
                          @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def spans = getTrace(writer, traceId)
    Stopwatch stopwatch = Stopwatch.createStarted()
    while (stopwatch.elapsed(TimeUnit.SECONDS) < 10) {
      if (spans.size() == expectedSize) {
        break
      }
      Thread.sleep(10)
      spans = getTrace(writer, traceId)
    }
    assert spans.size() == expectedSize
    def asserter = new TraceAssert(spans)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertSpansAllVerified()
  }

  List<SpanData> getSpans() {
    return spans
  }

  SpanData span(int index) {
    spans.get(index)
  }

  void span(int index, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= spans.size()) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    assertedIndexes.add(index)
    assertSpan(spans.get(index), spec)
  }

  void span(String name, @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    int index = -1
    for (int i = 0; i < spans.size(); i++) {
      if (spans[i].name == name) {
        index = i
        break
      }
    }
    span(index, spec)
  }

  // this doesn't provide any functionality, just a self-documenting marker
  void sortSpans(Closure callback) {
    callback.call()
  }

  void assertSpansAllVerified() {
    assert assertedIndexes.size() == spans.size()
  }

  private static List<SpanData> getTrace(ListWriter writer, TraceId traceId) {
    for (List<SpanData> trace : writer.getTraces()) {
      if (trace[0].traceId == traceId) {
        return trace
      }
    }
    throw new AssertionError("Trace not found: " + traceId)
  }
}
