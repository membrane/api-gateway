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
