package io.opentelemetry.auto.instrumentation.springwebflux.server;

import io.opentelemetry.auto.agent.tooling.Instrumenter;

public abstract class AbstractWebfluxInstrumentation extends Instrumenter.Default {

  public AbstractWebfluxInstrumentation(final String... additionalNames) {
    super("spring-webflux", additionalNames);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.agent.decorator.BaseDecorator",
      "io.opentelemetry.auto.agent.decorator.ServerDecorator",
      packageName + ".SpringWebfluxHttpServerDecorator",
      // Some code comes from reactor's instrumentation's helper
      "io.opentelemetry.auto.instrumentation.reactor.core.ReactorCoreAdviceUtils",
      "io.opentelemetry.auto.instrumentation.reactor.core.ReactorCoreAdviceUtils$TracingSubscriber",
      packageName + ".AdviceUtils",
      packageName + ".RouteOnSuccessOrError"
    };
  }
}