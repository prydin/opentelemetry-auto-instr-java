package io.opentelemetry.auto.instrumentation.elasticsearch5;

import static io.opentelemetry.auto.instrumentation.elasticsearch.ElasticsearchRestClientDecorator.DECORATE;

import io.opentelemetry.trace.Span;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Span span;

  public RestResponseListener(final ResponseListener listener, final Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onSuccess(final Response response) {
    if (response.getHost() != null) {
      DECORATE.onResponse(span, response);
    }

    try {
      listener.onSuccess(response);
    } finally {
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onFailure(final Exception e) {
    DECORATE.onError(span, e);

    try {
      listener.onFailure(e);
    } finally {
      DECORATE.beforeFinish(span);
      span.end();
    }
  }
}
