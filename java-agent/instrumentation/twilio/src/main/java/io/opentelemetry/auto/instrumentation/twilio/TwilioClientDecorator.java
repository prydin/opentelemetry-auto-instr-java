package io.opentelemetry.auto.instrumentation.twilio;

import com.google.common.util.concurrent.ListenableFuture;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ClientDecorator;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

/** Decorate Twilio span's with relevant contextual information. */
@Slf4j
public class TwilioClientDecorator extends ClientDecorator {

  public static final TwilioClientDecorator DECORATE = new TwilioClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.twilio");

  static final String COMPONENT_NAME = "twilio-sdk";

  @Override
  protected String spanType() {
    return SpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {COMPONENT_NAME};
  }

  @Override
  protected String component() {
    return COMPONENT_NAME;
  }

  @Override
  protected String service() {
    return COMPONENT_NAME;
  }

  /** Decorate trace based on service execution metadata. */
  public Span onServiceExecution(
      final Span span, final Object serviceExecutor, final String methodName) {

    // Drop common package prefix (com.twilio.rest)
    final String simpleClassName =
        serviceExecutor.getClass().getCanonicalName().replaceFirst("^com\\.twilio\\.rest\\.", "");

    span.setAttribute(MoreTags.RESOURCE_NAME, String.format("%s.%s", simpleClassName, methodName));

    return span;
  }

  /** Annotate the span with the results of the operation. */
  public Span onResult(final Span span, Object result) {

    // Unwrap ListenableFuture (if present)
    if (result instanceof ListenableFuture) {
      try {
        result = ((ListenableFuture) result).get(0, TimeUnit.MICROSECONDS);
      } catch (final Exception e) {
        log.debug("Error unwrapping result", e);
      }
    }

    // Nothing to do here, so return
    if (result == null) {
      return span;
    }

    // Provide helpful metadata for some of the more common response types
    span.setAttribute("twilio.type", result.getClass().getCanonicalName());

    // Instrument the most popular resource types directly
    if (result instanceof Message) {
      final Message message = (Message) result;
      span.setAttribute("twilio.account", message.getAccountSid());
      span.setAttribute("twilio.sid", message.getSid());
      if (message.getStatus() != null) {
        span.setAttribute("twilio.status", message.getStatus().toString());
      }
    } else if (result instanceof Call) {
      final Call call = (Call) result;
      span.setAttribute("twilio.account", call.getAccountSid());
      span.setAttribute("twilio.sid", call.getSid());
      if (call.getParentCallSid() != null) {
        span.setAttribute("twilio.parentSid", call.getParentCallSid());
      }
      if (call.getStatus() != null) {
        span.setAttribute("twilio.status", call.getStatus().toString());
      }
    } else {
      // Use reflection to gather insight from other types; note that Twilio requests take close to
      // 1 second, so the added hit from reflection here is relatively minimal in the grand scheme
      // of things
      setTagIfPresent(span, result, "twilio.sid", "getSid");
      setTagIfPresent(span, result, "twilio.account", "getAccountSid");
      setTagIfPresent(span, result, "twilio.status", "getStatus");
    }

    return span;
  }

  /**
   * Helper method for calling a getter using reflection. This will be slow, so only use when
   * required.
   */
  private void setTagIfPresent(
      final Span span, final Object result, final String tag, final String getter) {
    try {
      final Method method = result.getClass().getMethod(getter);
      final Object value = method.invoke(result);

      if (value != null) {
        span.setAttribute(tag, value.toString());
      }

    } catch (final Exception e) {
      // Expected that this won't work for all result types
    }
  }
}
