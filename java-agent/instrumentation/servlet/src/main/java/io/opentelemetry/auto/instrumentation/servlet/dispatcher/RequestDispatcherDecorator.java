package io.opentelemetry.auto.instrumentation.servlet.dispatcher;

import io.opentelemetry.auto.decorator.BaseDecorator;

public class RequestDispatcherDecorator extends BaseDecorator {
  public static final RequestDispatcherDecorator DECORATE = new RequestDispatcherDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet", "servlet-dispatcher"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "java-web-servlet-dispatcher";
  }
}
