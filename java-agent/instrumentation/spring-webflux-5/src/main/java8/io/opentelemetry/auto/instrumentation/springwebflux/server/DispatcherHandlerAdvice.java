package io.opentelemetry.auto.instrumentation.springwebflux.server;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.DECORATE;

import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.reactor.core.ReactorCoreAdviceUtils;
import java.util.function.Function;
import net.bytebuddy.asm.Advice;
import org.reactivestreams.Publisher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * This is 'top level' advice for Webflux instrumentation. This handles creating and finishing
 * Webflux span.
 */
public class DispatcherHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope methodEnter(@Advice.Argument(0) final ServerWebExchange exchange) {
    // Unfortunately Netty EventLoop is not instrumented well enough to attribute all work to the
    // right things so we have to store span in request itself. We also store parent (netty's) span
    // so we could update resource name.
    final AgentSpan parentSpan = activeSpan();
    if (parentSpan != null) {
      exchange.getAttributes().put(AdviceUtils.PARENT_SPAN_ATTRIBUTE, parentSpan);
    }

    final AgentSpan span = startSpan("DispatcherHandler.handle");
    DECORATE.afterStart(span);
    exchange.getAttributes().put(AdviceUtils.SPAN_ATTRIBUTE, span);

    return activateSpan(span, false);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final AgentScope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Argument(0) final ServerWebExchange exchange,
      @Advice.Return(readOnly = false) Mono<Object> mono) {
    if (throwable == null && mono != null) {
      final Function<? super Mono<Object>, ? extends Publisher<Object>> function =
          ReactorCoreAdviceUtils.finishSpanNextOrError();
      mono = ReactorCoreAdviceUtils.setPublisherSpan(mono, scope.span());
    } else if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    if (scope != null) {
      scope.close();
    }
  }
}
