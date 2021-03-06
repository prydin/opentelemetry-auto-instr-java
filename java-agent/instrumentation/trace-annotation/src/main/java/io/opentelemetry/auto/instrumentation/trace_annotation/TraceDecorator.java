package io.opentelemetry.auto.instrumentation.trace_annotation;

import io.opentelemetry.auto.decorator.BaseDecorator;

public class TraceDecorator extends BaseDecorator {
  public static TraceDecorator DECORATE = new TraceDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[0];
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "trace";
  }
}
