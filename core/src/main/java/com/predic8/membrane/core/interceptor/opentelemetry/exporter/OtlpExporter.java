package com.predic8.membrane.core.interceptor.opentelemetry.exporter;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

import static io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter.builder;
import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * Otlp Implementation for the OpenTelemetry protocol
 */
@MCElement(name = "otlpExporter", topLevel = false)
public class OtlpExporter implements OtelExporter {
    private String host = "localhost";
    private int port = 4317;

    public String getEndpointUrl() {
        return "http://" + getHost() + ":" + getPort();
    }

    public OtlpGrpcSpanExporter get() {
        return builder().setEndpoint(getEndpointUrl()).setTimeout(30, SECONDS).build();
    }

    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }
}
