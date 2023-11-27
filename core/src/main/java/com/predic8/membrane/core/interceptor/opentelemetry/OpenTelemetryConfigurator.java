/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import static com.predic8.membrane.core.Constants.VERSION;
import static io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance;
import static io.opentelemetry.context.propagation.ContextPropagators.create;
import static io.opentelemetry.sdk.trace.export.BatchSpanProcessor.builder;
import static io.opentelemetry.sdk.trace.samplers.Sampler.traceIdRatioBased;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_VERSION;
import static java.util.concurrent.TimeUnit.SECONDS;

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
                .build();
    }

    private static SdkTracerProvider getSdkTracerProvider(String endpoint, double sampleRate) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(builder(OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).setTimeout(30, SECONDS).build()).build())
                .setSampler(traceIdRatioBased(sampleRate))
                .setResource(Resource.getDefault().toBuilder().put(SERVICE_NAME, "Membrane").put(SERVICE_VERSION, VERSION ).build())
                .build();
    }
}
