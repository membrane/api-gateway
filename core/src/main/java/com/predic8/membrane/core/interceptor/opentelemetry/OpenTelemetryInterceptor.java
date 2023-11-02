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

import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.getContextFromRequestHeader;
import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.setContextInHeader;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.context.Context.current;


@MCElement(name = "opentelemetry")
public class OpenTelemetryInterceptor extends AbstractInterceptor {
    private String jaegerHost = "localhost";
    private String jaegerPort = "4317";
    private double sampleRate = 1.0;

    private static final String REQUEST = "REQUEST";
    private static final String RESPONSE = "RESPONSE";

    OpenTelemetry openTelemetryInstance;
    Tracer tracer;

    @Override
    public void init() throws Exception {
        openTelemetryInstance = OpenTelemetryConfigurator.openTelemetry("http://" + getJaegerHost() + ":" + getJaegerPort(), getSampleRate());
        tracer = openTelemetryInstance.getTracer("MEMBRANE-TRACER");
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        startMembraneScope(exc, getExtractContext(exc), true);
        return super.handleRequest(exc);
    }

    private void startMembraneScope(Exchange exc, Context receivedContext, boolean isRequest) {
        try(Scope ignore = receivedContext.makeCurrent()) {
            Span membraneSpan = getMembraneSpan(String.format("HANDLE-%s-SPAN", getRequestOrResponseString(isRequest)), String.format("MEMBRANE-INTERCEPTED-%s", getRequestOrResponseString(isRequest)));

            try(Scope ignored = membraneSpan.makeCurrent()) {
                membraneSpan.addEvent(String.format("STARTING-MEMBRANE-%s-CONTEXT-SPAN", getRequestOrResponseString(isRequest)));
                membraneSpan.addEvent(String.format("ENDING-MEMBRANE-%s-CONTEXT-SPAN", getRequestOrResponseString(isRequest)));

                setExchangeHeader(exc, isRequest);
            }finally {
                membraneSpan.end();
            }
        }
    }

    private Span getMembraneSpan(String spanName, String eventName) {
        return tracer.spanBuilder(spanName)
                .setSpanKind(INTERNAL)
                .startSpan()
                .addEvent(eventName);
    }

    private String getRequestOrResponseString(boolean isRequest) {
        if (isRequest) return REQUEST;
        return RESPONSE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        startMembraneScope(exc, getExtractContext(exc), false);
        return super.handleResponse(exc);
    }

    private void setExchangeHeader(Exchange exc, boolean isRequest) {
        openTelemetryInstance.getPropagators().getTextMapPropagator().inject(current(),
                exc,
                setContextInHeader(isRequest));
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
