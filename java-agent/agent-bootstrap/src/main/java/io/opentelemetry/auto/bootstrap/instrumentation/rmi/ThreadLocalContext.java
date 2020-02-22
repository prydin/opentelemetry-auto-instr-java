package io.opentelemetry.auto.bootstrap.instrumentation.rmi;

import io.opentelemetry.auto.instrumentation.api.AgentSpan;

public class ThreadLocalContext {
  public static final ThreadLocalContext THREAD_LOCAL_CONTEXT = new ThreadLocalContext();
  private final ThreadLocal<AgentSpan.Context> local;

  public ThreadLocalContext() {
    local = new ThreadLocal<>();
  }

  public void set(final AgentSpan.Context context) {
    local.set(context);
  }

  public AgentSpan.Context getAndResetContext() {
    final AgentSpan.Context context = local.get();
    if (context != null) {
      local.remove();
    }
    return context;
  }
}
