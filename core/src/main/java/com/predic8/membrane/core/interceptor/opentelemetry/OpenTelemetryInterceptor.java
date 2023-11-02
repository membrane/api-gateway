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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.opentelemetry.HTTPTraceContextUtil.*;
import static io.opentelemetry.api.trace.SpanKind.*;
import static io.opentelemetry.context.Context.*;


@MCElement(name = "opentelemetry")
public class OpenTelemetryInterceptor extends AbstractInterceptor {
    private String jaegerHost = "localhost";
    private String jaegerPort = "4317";
    private double sampleRate = 1.0;

    private  final Logger log = LoggerFactory.getLogger(OpenTelemetryInterceptor.class);
    OpenTelemetry openTelemetryInstance;
    Tracer tracer;

    @Override
    public void init() throws Exception {
        openTelemetryInstance = OpenTelemetryConfigurator.openTelemetry("http://" + getJaegerHost() + ":" + getJaegerPort(), getSampleRate());
        tracer = openTelemetryInstance.getTracer("MEMBRANE-TRACER");
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        // extract context from header
        Context receivedContext = getExtractContext(exc);

        try(Scope ignore = receivedContext.makeCurrent()) {
            Span membraneSpan = tracer.spanBuilder("HANDLE-REQUEST-SPAN")
                    .setSpanKind(INTERNAL)
                    .startSpan()
                    .addEvent("MEMBRANE-INTERCEPTED-REQUEST");

            try(Scope ignored = membraneSpan.makeCurrent()) {
                membraneSpan.addEvent("STARTING-MEMBRANE-CONTEXT-SPAN");
                // ... do something here???
                membraneSpan.addEvent("ENDING-MEMBRANE-CONTEXT-SPAN");
                //inject context into header
                setExchangeHeader(exc);
            }finally {
                membraneSpan.end();
            }
        }
        return super.handleRequest(exc);
    }

    private void setExchangeHeader(Exchange exc) {
        openTelemetryInstance.getPropagators().getTextMapPropagator().inject(current(),
                exc,
                remoteContextSetter());
    }

    private Context getExtractContext(Exchange exc) {
        return openTelemetryInstance
                .getPropagators().
                getTextMapPropagator()
                .extract(current(), exc, remoteContextGetter());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return super.handleResponse(exc);
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
