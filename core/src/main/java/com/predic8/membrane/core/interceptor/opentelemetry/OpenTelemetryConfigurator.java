package com.predic8.membrane.core.interceptor.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.util.concurrent.TimeUnit;

import static io.opentelemetry.semconv.ResourceAttributes.*;

public class OpenTelemetryConfigurator {
    public static OpenTelemetry openTelemetry(String endpoint, double sampleRate) {
        Resource resource = Resource.getDefault().toBuilder().put(SERVICE_NAME, "Membrane-Internal-Service").put(SERVICE_VERSION, "1.0.0").build();

        // can't inline because of the shutdown. otherwise the data won't be flushed and sent to the jaeger backend!
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).setTimeout(30, TimeUnit.SECONDS).build()).build())
                .setSampler(Sampler.traceIdRatioBased(sampleRate))
                .setResource(resource)
                .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())))
                .buildAndRegisterGlobal();
        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));

        return openTelemetry;
    }
}
