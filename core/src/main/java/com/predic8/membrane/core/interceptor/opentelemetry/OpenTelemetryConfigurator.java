package com.predic8.membrane.core.interceptor.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import static io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.*;
import static io.opentelemetry.context.propagation.ContextPropagators.*;
import static io.opentelemetry.sdk.trace.export.BatchSpanProcessor.*;
import static io.opentelemetry.sdk.trace.samplers.Sampler.*;
import static io.opentelemetry.semconv.ResourceAttributes.*;
import static java.util.concurrent.TimeUnit.*;

public class OpenTelemetryConfigurator {
    public static OpenTelemetry openTelemetry(String endpoint, double sampleRate) {

        // can't inline because of the shutdown. otherwise the data won't be flushed and sent to the jaeger backend!
        SdkTracerProvider sdkTracerProvider = getSdkTracerProvider(endpoint, sampleRate);
        OpenTelemetry openTelemetry = getGlobalOpenTelemetry(sdkTracerProvider);
        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
        return openTelemetry;
    }

    private static OpenTelemetrySdk getGlobalOpenTelemetry(SdkTracerProvider sdkTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(create(TextMapPropagator.composite(getInstance(), W3CBaggagePropagator.getInstance())))
                .buildAndRegisterGlobal();
    }

    private static SdkTracerProvider getSdkTracerProvider(String endpoint, double sampleRate) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(builder(OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).setTimeout(30, SECONDS).build()).build())
                .setSampler(traceIdRatioBased(sampleRate))
                .setResource(Resource.getDefault().toBuilder().put(SERVICE_NAME, "Membrane-Internal-Service").put(SERVICE_VERSION, "1.0.0").build())
                .build();
    }
}
