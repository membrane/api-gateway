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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.opentelemetry.exporter.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import io.opentelemetry.api.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import org.slf4j.*;

import java.io.IOException;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor.*;
import static io.opentelemetry.api.common.AttributeKey.*;
import static io.opentelemetry.api.common.Attributes.*;
import static io.opentelemetry.api.trace.SpanKind.*;
import static io.opentelemetry.api.trace.StatusCode.*;
import static io.opentelemetry.context.Context.*;


@MCElement(name = "openTelemetry")
public class OpenTelemetryInterceptor extends AbstractInterceptor {
    private double sampleRate = 1.0;
    private OtelExporter exporter = new OtlpExporter();
    private OpenTelemetry otel;
    private Tracer tracer;

    private boolean logBody = false;

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryInterceptor.class);

    @Override
    public void init() {
        super.init();
        otel = OpenTelemetryConfigurator.openTelemetry("Membrane", exporter, getSampleRate());
        tracer = otel.getTracer("MEMBRANE-TRACER");

    }

    public OpenTelemetryInterceptor() {
        name = "opentelemetry exporter";
    }

    @Override
    public String getShortDescription() {
        return "Exports OpenTelemetry data to a specified collector.";
    }

    @Override
    public String getLongDescription() {
        return getShortDescription() + "<br/>" +
                "Collector: " + exporter.getEndpointUrl();
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws IOException {
        startMembraneScope(exc, getExtractContext(exc), getSpanName(exc)); // Params in Methode
        var span = getExchangeSpan(exc);
        setSpanHttpHeaderAttributes(exc.getRequest().getHeader(), span);
        try {
            if (logBody) {
                span.addEvent("Request", of(
                        stringKey("Request Body"), exc.getRequest().getBodyAsStringDecoded()
                ));
            }
        } catch (IOException e) {
            log.debug(e.getMessage());
            exc.setResponse(new Response.ResponseBuilder().status(400).build());
            return RETURN;
        }

        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        var span = getExchangeSpan(exc);
        span.setStatus(getOtelStatusCode(exc));
        span.setAttribute("http.status_code", exc.getResponse().getStatusCode());
        setSpanHttpHeaderAttributes(exc.getResponse().getHeader(), span);
        if (exc.getProxy() instanceof APIProxy) {
            setSpanOpenAPIAttributes(exc, span);
        }

        try {
            if (logBody) {
                span.addEvent("Response", of(
                        stringKey("Response Body"), exc.getResponse().getBodyAsStringDecoded()
                ));
            }
        } catch (IOException e) {
            log.debug(e.getMessage());
            return RETURN;
        }

        span.addEvent("Close Exchange").end();
        return CONTINUE;
    }

    private static Span getExchangeSpan(Exchange exc) {
        return exc.getProperty("span", Span.class);
    }

    private static void setSpanHttpHeaderAttributes(Header header, Span span) {
        for (HeaderField hf : header.getAllHeaderFields()) {
            span.setAttribute("http.header." + hf.getHeaderName().toString(), hf.getValue());
        }
    }

    private void setSpanOpenAPIAttributes(Exchange exc, Span span) {
        OpenAPIRecord record;
        try {
            record = exc.getProperty(OPENAPI_RECORD, OpenAPIRecord.class);
        } catch (ClassCastException e) {
            log.debug("No OpenAPI to report to OpenTelemetry.");
            return;
        }
        if (record != null) {
            span.setAttribute("openapi.title", record.getApi().getInfo().getTitle());
        }
    }

    private String getSpanName(Exchange exc) {
        return getProxyName(exc) + " " + exc.getRequest().getMethod() + " " + exc.getRequest().getUri();
    }

    private String getProxyName(Exchange exc) {
        var r = exc.getProxy();
        return r.getName().isEmpty() ?
                r.getKey().getHost() + r.getKey().getPort()
                : r.getName();
    }

    private StatusCode getOtelStatusCode(Exchange exc) {
        return exc.getResponse().getStatusCode() >= 500 ? ERROR : OK;
    }

    private void startMembraneScope(Exchange exc, Context receivedContext, String spanName) {
        try (Scope ignore = receivedContext.makeCurrent()) {
            Span membraneSpan = getMembraneSpan(spanName, "Initialize Exchange");

            try (Scope ignored = membraneSpan.makeCurrent()) {
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
