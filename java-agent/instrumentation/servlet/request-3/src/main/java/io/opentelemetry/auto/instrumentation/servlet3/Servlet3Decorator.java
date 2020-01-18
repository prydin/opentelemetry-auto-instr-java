package io.opentelemetry.auto.instrumentation.servlet3;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;

public class Servlet3Decorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, HttpServletResponse> {
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  public static final Servlet3Decorator DECORATE = new Servlet3Decorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"servlet", "servlet-3"};
  }

  @Override
  protected String component() {
    return "java-web-servlet";
  }

  @Override
  protected String method(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  @Override
  protected URI url(final HttpServletRequest httpServletRequest) throws URISyntaxException {
    return new URI(
        httpServletRequest.getScheme(),
        null,
        httpServletRequest.getServerName(),
        httpServletRequest.getServerPort(),
        httpServletRequest.getRequestURI(),
        httpServletRequest.getQueryString(),
        null);
  }

  @Override
  protected String peerHostname(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteHost();
  }

  @Override
  protected String peerHostIP(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemotePort();
  }

  @Override
  protected Integer status(final HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  public Span onRequest(final Span span, final HttpServletRequest request) {
    assert span != null;
    if (request != null) {
      final String sc = request.getContextPath();
      if (sc != null && sc != "") {
        span.setAttribute("servlet.context", sc);
      }
      span.setAttribute("servlet.path", request.getServletPath());
    }
    return super.onRequest(span, request);
  }

  @Override
  public Span onError(final Span span, final Throwable throwable) {
    if (throwable instanceof ServletException && throwable.getCause() != null) {
      super.onError(span, throwable.getCause());
    } else {
      super.onError(span, throwable);
    }
    return span;
  }
}
