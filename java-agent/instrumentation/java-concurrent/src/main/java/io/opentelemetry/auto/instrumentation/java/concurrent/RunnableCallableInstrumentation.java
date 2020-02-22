package io.opentelemetry.auto.instrumentation.java.concurrent;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.State;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument {@link Runnable} and {@Callable} */
@Slf4j
@AutoService(Instrumenter.class)
public final class RunnableCallableInstrumentation extends Instrumenter.Default {

  public RunnableCallableInstrumentation() {
    super(AbstractExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named(Runnable.class.getName()).or(named(Callable.class.getName()))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AdviceUtils.class.getName(),
    };
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put(Runnable.class.getName(), State.class.getName());
    map.put(Callable.class.getName(), State.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("run").and(takesArguments(0)).and(isPublic()),
        RunnableCallableInstrumentation.class.getName() + "$RunnableAdvice");
    transformers.put(
        named("call").and(takesArguments(0)).and(isPublic()),
        RunnableCallableInstrumentation.class.getName() + "$CallableAdvice");
    return transformers;
  }

  public static class RunnableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope enter(@Advice.This final Runnable thiz) {
      final ContextStore<Runnable, State> contextStore =
          InstrumentationContext.get(Runnable.class, State.class);
      return AdviceUtils.startTaskScope(contextStore, thiz);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final AgentScope scope) {
      AdviceUtils.endTaskScope(scope);
    }
  }

  public static class CallableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope enter(@Advice.This final Callable thiz) {
      final ContextStore<Callable, State> contextStore =
          InstrumentationContext.get(Callable.class, State.class);
      return AdviceUtils.startTaskScope(contextStore, thiz);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final AgentScope scope) {
      AdviceUtils.endTaskScope(scope);
    }
  }
}
