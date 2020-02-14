package io.opentelemetry.auto.exporters.jaeger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.auto.exportersupport.ConfigProvider;
import io.opentelemetry.auto.exportersupport.SpanExporterFactory;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class JaegerExporterFactory implements SpanExporterFactory {
  private static final String HOST_CONFIG = "jaeger.host";

  private static final String PORT_CONFIG = "jaeger.port";

  private static final String SERVICE_CONFIG = "service";

  private static final int DEFAULT_PORT = 14250;

  private static final String DEFAULT_SERVICE = "(unknown service)";

  @Override
  public SpanExporter fromConfig(final ConfigProvider config) {
    final String host = config.getString(HOST_CONFIG, null);
    if (host == null) {
      throw new IllegalArgumentException(HOST_CONFIG + " must be specified");
    }
    final int port = config.getInt(PORT_CONFIG, DEFAULT_PORT);
    final String service = config.getString(SERVICE_CONFIG, DEFAULT_SERVICE);
    final ManagedChannel jaegerChannel =
        ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    return JaegerGrpcSpanExporter.newBuilder()
        .setServiceName(service)
        .setChannel(jaegerChannel)
        .setDeadline(30000)
        .build();
  }
}
