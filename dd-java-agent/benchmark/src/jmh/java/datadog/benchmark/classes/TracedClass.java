package datadog.benchmark.classes;

import io.opentelemetry.auto.api.Trace;

public class TracedClass extends UntracedClass {
  @Trace
  @Override
  public void f() {}

  @Trace
  @Override
  public void e() {}

  @Trace
  @Override
  public void d() {}

  @Trace
  @Override
  public void c() {}

  @Trace
  @Override
  public void b() {}

  @Trace
  @Override
  public void a() {}
}
