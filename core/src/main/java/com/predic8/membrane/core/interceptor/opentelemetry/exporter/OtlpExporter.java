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
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.interceptor.addHeader;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.interceptor.opentelemetry.exporter.OtlpExporter.OtlpType.*;
import static java.lang.String.format;

/*
 * Otlp Implementation for the OpenTelemetry protocol
 */
@MCElement(name = "otlpExporter", topLevel = false)
public class OtlpExporter implements OtelExporter {

    private static final int TIMEOUT_SECONDS = 30;
    private String host = "localhost";
    private Integer port;
    private String path = "";
    private OtlpType transport = GRPC;
    private final List<addHeader> headers = new ArrayList<>();

    private boolean secured = false;

    public String getEndpointUrl() {
        return format("%s://%s:%d%s", isSecured() ? "https" : "http", host, getProtocolPort(port, transport), getPathExtension());
    }

    @SuppressWarnings("StringEquality")
    private String getPathExtension() {
        if (path == "" && transport == HTTP) {
            return "/v1/traces";
        }
        return path;
    }

    private int getProtocolPort(Integer port, OtlpType trans) {
        if (port == null) {
            return switch (trans) {
                case HTTP -> 4318;
                case GRPC -> 4317;
            };
        }
        return port;
    }

    public SpanExporter get() {
        String endpointUrl = getEndpointUrl();
        return createSpanExporter(endpointUrl, headers.stream().map(addHeader::asHeaderField).toList());
    }

    private SpanExporter createSpanExporter(String endpointUrl, List<HeaderField> headers) {
        switch (transport) {
            case GRPC -> {
                return buildGrpcExporter(endpointUrl);
            }
            case HTTP -> {
                return buildHttpExporter(endpointUrl, headers);
            }
            default -> throw new IllegalArgumentException("Unsupported transport type");
        }
    }

    private OtlpGrpcSpanExporter buildGrpcExporter(String endpointUrl) {
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpointUrl)
                .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private OtlpHttpSpanExporter buildHttpExporter(String endpointUrl, List<HeaderField> headers) {
        var builder = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpointUrl)
                .setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        for (HeaderField header : headers) {
            builder.addHeader(header.getHeaderName().toString(), header.getValue());
        }

        return builder.build();
    }

    @MCChildElement
    public void setHeaders(List<addHeader> headers) {
        this.headers.addAll(headers);
    }

    @MCAttribute
    public void setTransport(String transport) {
        this.transport = OtlpType.fromString(transport);
    }

    @SuppressWarnings("unused")
    @MCAttribute
    public void setSecured(boolean secured) {
        this.secured = secured;
    }

    @MCAttribute
    public void setPath(String path) { this.path = path; }

    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() { return path; }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    public List<addHeader> getHeaders() {
        return headers;
    }

    public boolean isSecured() {
        return secured;
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