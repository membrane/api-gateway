package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@MCElement(name = "otelInterceptor")
public class OpenTelemetryInterceptor extends AbstractInterceptor {
    private String jaegerHost = "localhost";
    private String jaegerPort = "4317";

    private  final Logger log = LoggerFactory.getLogger(OpenTelemetryInterceptor.class);
    OpenTelemetry openTelemetryInstance = OpenTelemetryConfigurator.openTelemetry("http://" + getJaegerHost() + ":" + getJaegerPort());
    Tracer tracer = openTelemetryInstance.getTracer("MEMBRANE-TRACER");

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        // extract context from header
        Context receivedContext = openTelemetryInstance
                .getPropagators().
                getTextMapPropagator()
                .extract(Context.current(),exc,HTTPTraceContextUtil.remoteContextGetter());

        try(Scope membraneScope = receivedContext.makeCurrent()) {
            Span membraneSpan = tracer.spanBuilder("HANDLE-REQUEST-SPAN")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setParent(receivedContext)
                    .startSpan()
                    .addEvent("MEMBRANE-INTERCEPTED-REQUEST");
            receivedContext.with(membraneSpan);

            try(Scope membraneInternalScope = membraneSpan.makeCurrent()) {
                membraneSpan.addEvent("STARTING-MEMBRANE-CONTEXT-SPAN");
                // ... do something here???
                membraneSpan.addEvent("ENDING-MEMBRANE-CONTEXT-SPAN");
                //inject context into header
                openTelemetryInstance.getPropagators().getTextMapPropagator().inject(Context.current(),
                        exc,
                        HTTPTraceContextUtil.remoteContextSetter());
            }finally {
                membraneSpan.end();
            }
        }
        return super.handleRequest(exc);
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

    public String getJaegerHost() {
        return jaegerHost;
    }

    public String getJaegerPort() {
        return jaegerPort;
    }
}
