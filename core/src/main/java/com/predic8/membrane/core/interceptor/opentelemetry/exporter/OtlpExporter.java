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

package com.predic8.membrane.core.interceptor.opentelemetry.exporter;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.interceptor.opentelemetry.exporter.OtlpExporter.OtlpType.GRPC;

/*
 * Otlp Implementation for the OpenTelemetry protocol
 */
@MCElement(name = "otlpExporter", topLevel = false)
public class OtlpExporter implements OtelExporter {

    private static final int DEFAULT_PORT = 4317;
    private static final String DEFAULT_HOST = "localhost";
    private static final int TIMEOUT_SECONDS = 30;

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private OtlpType transport = GRPC;

    public String getEndpointUrl() {
        String endpoint = String.format("http://%s:%d", host, port);
        if (transport == OtlpType.HTTP) {
            endpoint += "/v1/traces";
        }
        return endpoint;
    }

    public SpanExporter get() {
        String endpointUrl = getEndpointUrl();
        return createSpanExporter(endpointUrl);
    }

    private SpanExporter createSpanExporter(String endpointUrl) {
        return switch (transport) {
            case GRPC -> OtlpGrpcSpanExporter.builder()
                    .setEndpoint(endpointUrl)
                    .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();
            case HTTP -> OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpointUrl)
                    .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();
        };
    }

    @MCAttribute
    public void setTransport(String transport) {
        this.transport = OtlpType.fromString(transport);
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
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    public enum OtlpType {
        HTTP,
        GRPC;

        @Override
        public String toString() {
            return name();
        }

        public static OtlpType fromString(String str) {
            for (OtlpType type : OtlpType.values()) {
                if (type.name().equalsIgnoreCase(str)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Illegal type for OtlpExporter: " + str);
        }
    }
}
