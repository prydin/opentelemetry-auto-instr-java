package io.opentelemetry.auto.instrumentation.java.concurrent;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.State;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class NonStandardExecutorInstrumentation extends AbstractExecutorInstrumentation {

  public NonStandardExecutorInstrumentation() {
    super(EXEC_NAME + ".other");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(Runnable.class.getName(), State.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put( // kotlinx.coroutines.scheduling.CoroutineScheduler
        named("dispatch")
            .and(takesArgument(0, Runnable.class))
            .and(takesArgument(1, named("kotlinx.coroutines.scheduling.TaskContext"))),
        JavaExecutorInstrumentation.class.getName() + "$SetExecuteRunnableStateAdvice");

    transformers.put( // org.eclipse.jetty.util.thread.QueuedThreadPool
        named("dispatch").and(takesArguments(1)).and(takesArgument(0, Runnable.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetExecuteRunnableStateAdvice");
    return transformers;
  }
}
