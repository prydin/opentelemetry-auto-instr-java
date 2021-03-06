package io.opentelemetry.auto.instrumentation.playws1;

import io.opentelemetry.auto.instrumentation.api.AgentPropagation;
import play.shaded.ahc.org.asynchttpclient.Request;

public class HeadersInjectAdapter implements AgentPropagation.Setter<Request> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(final Request carrier, final String key, final String value) {
    carrier.getHeaders().add(key, value);
  }
}
