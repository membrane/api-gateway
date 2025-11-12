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
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.getContextFromRequestHeader;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.setContextInHeader;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor.OPENAPI_RECORD;
import static com.predic8.membrane.core.util.ExceptionUtil.concatMessageAndCauseMessages;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.common.Attributes.of;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.api.trace.StatusCode.OK;
import static io.opentelemetry.context.Context.current;

/**
 * @description Creates an OpenTelemetry span for each HTTP request passing through. Sends the tracing data to the
 * speficied OpenTelemetry collector.
 *
 * See also examples/monitoring-tracing/opentelemetry for a demo, including screenshots.
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "openTelemetry")
public class OpenTelemetryInterceptor extends AbstractInterceptor {
    private double sampleRate = 1.0;
    private OtelExporter exporter = new OtlpExporter();
    private OpenTelemetry otel;
    private Tracer tracer;
    private final List<CustomAttribute> customAttributes = new ArrayList<>();

    private boolean logBody = false;

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryInterceptor.class);

    @Override
    public void init() {
        super.init();
        try {
            if (!SLF4JBridgeHandler.isInstalled()) {
                SLF4JBridgeHandler.removeHandlersForRootLogger();
                SLF4JBridgeHandler.install();
            }
        } catch (Throwable t) {
            log.warn("jul-to-slf4j not available; OpenTelemetry logs may go to stderr.", t);
        }

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
    public Outcome handleRequest(Exchange exc) {
        startMembraneScope(exc, getExtractContext(exc), getSpanName(exc)); // Params in Methode
        var span = getExchangeSpan(exc);
        setSpanHttpHeaderAttributes(exc.getRequest().getHeader(), span);
        setSpanCustomAttributes(span);

        if (!logBody)
            return CONTINUE;

        // try is needed to catch network errors in getBodyAsStringDecoded()
        try {
            span.addEvent("Request", of(
                    stringKey("Request Body"), exc.getRequest().getBodyAsStringDecoded()
            ));
        } catch (Exception e) {
            log.debug("Can't log request body having problems to read stream. {}", concatMessageAndCauseMessages(e));
        }

        return CONTINUE;
    }

    private void setSpanCustomAttributes(Span span) {
        for (CustomAttribute customAttribute : customAttributes) {
            span.setAttribute(stringKey(customAttribute.getName()), customAttribute.getValue());
        }
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

        if (logBody) {
            // try is needed to catch network errors in getBodyAsStringDecoded()
            try {
                span.addEvent("Response", of(
                        stringKey("Response Body"),
                        exc.getResponse().getBodyAsStringDecoded()
                ));
            } catch (Exception e) {
                log.debug("Can't log response body having problems to read stream. {}", concatMessageAndCauseMessages(e));
            }
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
        try(Scope ignore = receivedContext.makeCurrent()) {
            Span membraneSpan = getMembraneSpan(spanName, "Initialize Exchange");

            try(Scope ignored = membraneSpan.makeCurrent()) {
                setExchangeHeader(exc);
                exc.setProperty("span", membraneSpan);
                exc.setProperty("tracer", tracer);
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

    public List<CustomAttribute> getCustomAttributes() {
        return customAttributes;
    }

    @MCChildElement(order = 20)
    public void setCustomAttributes(List<CustomAttribute> customAttributes) {
        this.customAttributes.addAll(customAttributes);
    }

    @MCAttribute
    public void setLogBody(boolean logBody) {
        this.logBody = logBody;
    }

    public boolean getLogBody() {
        return logBody;
    }

    @MCChildElement(order = 10)
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

    @MCElement(name = "customAttribute", topLevel = false)
    public static class CustomAttribute {

        private String name;
        private String value;

        @MCAttribute
        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @MCAttribute
        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
