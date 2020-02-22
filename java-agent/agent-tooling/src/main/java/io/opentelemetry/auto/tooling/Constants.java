package io.opentelemetry.auto.tooling;

/**
 * Some useful constants.
 *
 * <p>Idea here is to keep this class safe to inject into client's class loader.
 */
public final class Constants {

  /**
   * packages which will be loaded on the bootstrap classloader
   *
   * <p>Updates should be mirrored in
   * io.opentelemetry.auto.test.SpockRunner#BOOTSTRAP_PACKAGE_PREFIXES_COPY
   */
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES = {
    "io.opentelemetry.auto.slf4j",
    "io.opentelemetry.auto.api",
    "io.opentelemetry.auto.bootstrap",
    "io.opentelemetry.auto.instrumentation.api",
    "io.opentelemetry.auto.shaded"
  };

  // This is used in IntegrationTestUtils.java
  public static final String[] AGENT_PACKAGE_PREFIXES = {
    "io.opentelemetry.auto",
    "io.opentelemetry.auto.instrumentation",
    // guava
    "com.google.auto",
    "com.google.common",
    "com.google.thirdparty.publicsuffix",
    // WeakConcurrentMap
    "com.blogspot.mydailyjava.weaklockfree",
    // bytebuddy
    "net.bytebuddy",
    "org.yaml.snakeyaml",
    // disruptor
    "com.lmax.disruptor",
    // okHttp
    "okhttp3",
    "okio",
    "jnr",
    "org.objectweb.asm",
    "com.kenai",
    // Custom RxJava Utility
    "rx.__OpenTelemetryTracingUtil",
  };

  private Constants() {}
}
