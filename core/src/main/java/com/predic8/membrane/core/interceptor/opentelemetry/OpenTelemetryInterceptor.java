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
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.opentelemetry.exporter.OtelExporter;
import com.predic8.membrane.core.interceptor.opentelemetry.exporter.OtlpExporter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.getContextFromRequestHeader;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.setContextInHeader;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.common.Attributes.of;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.api.trace.StatusCode.OK;
import static io.opentelemetry.context.Context.current;


@MCElement(name = "openTelemetry")
public class OpenTelemetryInterceptor extends AbstractInterceptor {
    private double sampleRate = 1.0;
    private OtelExporter exporter = new OtlpExporter();
    private OpenTelemetry otel;
    private Tracer tracer;

    private boolean logBody = false;

    @Override
    public void init() throws Exception {
        otel = OpenTelemetryConfigurator.openTelemetry("Membrane", exporter, getSampleRate());
        tracer = otel.getTracer("MEMBRANE-TRACER");
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        startMembraneScope(exc, getExtractContext(exc), getSpanName(exc)); // Params in Methode
        var span = getExchangeSpan(exc);
        setSpanHttpHeaderTags(exc.getRequest().getHeader(), span);

//        span.addEvent("Request", of(
//                stringKey("Request Header"), exc.getRequest().getHeader().toString()
//        ));

        if (logBody) {
            span.addEvent("Request", of(
                    stringKey("Request Body"), exc.getRequest().getBodyAsStringDecoded()
            ));
        }

        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        var span = getExchangeSpan(exc);
        span.setStatus(getOtelStatusCode(exc));
        span.setAttribute("http.status_code", exc.getResponse().getStatusCode());
        setSpanHttpHeaderTags(exc.getResponse().getHeader(), span);

        if (logBody) {
            span.addEvent("Response", of(
                    stringKey("Response Body"), exc.getResponse().getBodyAsStringDecoded()
            ));
        }

        span.addEvent("Close Exchange").end();
        return CONTINUE;
    }

    private static Span getExchangeSpan(Exchange exc) {
        return ((Span) exc.getProperty("span"));
    }

    private static void setSpanHttpHeaderTags(Header header, Span span) {
        for (HeaderField hf : header.getAllHeaderFields()) {
            span.setAttribute("http.header." + hf.getHeaderName().toString(), hf.getValue());
        }
    }

    private String getSpanName(Exchange exc) {
        return getProxyName(exc) + " " + exc.getRequest().getMethod() + " " + exc.getRequest().getUri();
    }

    private String getProxyName(Exchange exc) {
        var r = exc.getRule();
        return r.getName().isEmpty() ?
               r.getKey().getHost() + r.getKey().getPort()
               : r.getName();
    }

    private StatusCode getOtelStatusCode(Exchange exc) {
        return exc.getResponse().getStatusCode() >= 500 ? ERROR : OK;
    }

    private void startMembraneScope(Exchange exc, Context receivedContext, String spanName) {
        try(Scope ignore = receivedContext.makeCurrent()) {
            Span membraneSpan = getMembraneSpan(spanName, "Initialize Exchange");

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
        otel.getPropagators().getTextMapPropagator().inject(current(),
                exc,
                setContextInHeader());
    }

    private Context getExtractContext(Exchange exc) {
        return otel
                .getPropagators()
                .getTextMapPropagator()
                .extract(current(), exc, getContextFromRequestHeader());
    }

    @MCAttribute
    public void setLogBody(boolean logBody) {
        this.logBody = logBody;
    }

    public boolean getLogBody() {
        return logBody;
    }

    @MCChildElement
    public void setExporter(OtelExporter exporter) {
        this.exporter = exporter;
    }

    public OtelExporter getExporter() {
        return exporter;
    }

    @MCAttribute
    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public double getSampleRate() {
        return sampleRate;
    }
}
