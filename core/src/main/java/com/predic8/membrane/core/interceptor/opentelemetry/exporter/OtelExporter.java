package com.predic8.membrane.core.interceptor.opentelemetry.exporter;

import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Interface for all possible protokolls like Otlp, Jaeger or Zipkin
 */
public interface OtelExporter {
    String getHost();
    int getPort();
    void setHost(String host);
    void setPort(int port);
    String getEndpointUrl();
    SpanExporter get();
}
