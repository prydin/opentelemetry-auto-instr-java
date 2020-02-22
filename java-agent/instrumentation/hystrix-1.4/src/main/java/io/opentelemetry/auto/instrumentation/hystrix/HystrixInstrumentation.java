package io.opentelemetry.auto.instrumentation.hystrix;

import static io.opentelemetry.auto.instrumentation.hystrix.HystrixDecorator.DECORATE;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.auto.instrumentation.rxjava.TracedOnSubscribe;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.Observable;

@AutoService(Instrumenter.class)
public class HystrixInstrumentation extends Instrumenter.Default {

  private static final String OPERATION_NAME = "hystrix.cmd";

  public HystrixInstrumentation() {
    super("hystrix");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        named("com.netflix.hystrix.HystrixCommand")
            .or(named("com.netflix.hystrix.HystrixObservableCommand")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "rx.__OpenTelemetryTracingUtil",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.instrumentation.rxjava.SpanFinishingSubscription",
      "io.opentelemetry.auto.instrumentation.rxjava.TracedSubscriber",
      "io.opentelemetry.auto.instrumentation.rxjava.TracedOnSubscribe",
      packageName + ".HystrixDecorator",
      packageName + ".HystrixInstrumentation$HystrixOnSubscribe",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("getExecutionObservable").and(returns(named("rx.Observable"))),
        HystrixInstrumentation.class.getName() + "$ExecuteAdvice");
    transformers.put(
        named("getFallbackObservable").and(returns(named("rx.Observable"))),
        HystrixInstrumentation.class.getName() + "$FallbackAdvice");
    return transformers;
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable result,
        @Advice.Thrown final Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe(result, command, "execute"));
    }
  }

  public static class FallbackAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable<?> result,
        @Advice.Thrown final Throwable throwable) {

      result = Observable.create(new HystrixOnSubscribe(result, command, "fallback"));
    }
  }

  public static class HystrixOnSubscribe extends TracedOnSubscribe {
    private final HystrixInvokableInfo<?> command;
    private final String methodName;

    public HystrixOnSubscribe(
        final Observable originalObservable,
        final HystrixInvokableInfo<?> command,
        final String methodName) {
      super(originalObservable, OPERATION_NAME, DECORATE);

      this.command = command;
      this.methodName = methodName;
    }

    @Override
    protected void afterStart(final Span span) {
      super.afterStart(span);

      DECORATE.onCommand(span, command, methodName);
    }
  }
}
