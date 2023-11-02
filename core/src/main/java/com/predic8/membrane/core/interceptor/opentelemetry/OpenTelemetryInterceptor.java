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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.getContextFromRequestHeader;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.setContextInHeader;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.context.Context.current;


@MCElement(name = "opentelemetry")
public class OpenTelemetryInterceptor extends AbstractInterceptor {
    private String jaegerHost = "localhost";
    private String jaegerPort = "4317";
    private double sampleRate = 1.0;

    private OpenTelemetry openTelemetryInstance;
    private Tracer tracer;

    @Override
    public void init() throws Exception {
        openTelemetryInstance = OpenTelemetryConfigurator.openTelemetry("http://" + getJaegerHost() + ":" + getJaegerPort(), getSampleRate());
        tracer = openTelemetryInstance.getTracer("MEMBRANE-TRACER");
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        startMembraneScope(exc, getExtractContext(exc));
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        ((Span) exc.getProperty("span")).addEvent("MEMBRANE-RESPONSE").end();
        return CONTINUE;
    }

    private void startMembraneScope(Exchange exc, Context receivedContext) {
        try(Scope ignore = receivedContext.makeCurrent()) {
            Span membraneSpan = getMembraneSpan("MEMBRANE-REQUEST", "MEMBRANE-START");

            try(Scope ignored = membraneSpan.makeCurrent()) {
                setExchangeHeader(exc);
                exc.setProperty("span", membraneSpan);
            }
        }
    }

    private Span getMembraneSpan(String spanName, String eventName) {
        return tracer.spanBuilder(spanName)
                .setSpanKind(INTERNAL)
                .startSpan()
                .addEvent(eventName);
    }

    private void setExchangeHeader(Exchange exc) {
        openTelemetryInstance.getPropagators().getTextMapPropagator().inject(current(),
                exc,
                setContextInHeader());
    }

    private Context getExtractContext(Exchange exc) {
        return openTelemetryInstance
                .getPropagators().
                getTextMapPropagator()
                .extract(current(), exc, getContextFromRequestHeader());
    }

    @MCAttribute
    public void setJaegerHost(String jaegerHost) {
        this.jaegerHost = jaegerHost;
    }

    @MCAttribute
    public void setJaegerPort(String jaegerPort) {
        this.jaegerPort = jaegerPort;
    }

    @MCAttribute
    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getJaegerHost() {
        return jaegerHost;
    }

    public String getJaegerPort() {
        return jaegerPort;
    }

    public double getSampleRate() {
        return sampleRate;
    }
}