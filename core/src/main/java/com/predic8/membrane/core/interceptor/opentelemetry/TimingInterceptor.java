package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowWithChildrenInterceptor;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.opentelemetry.OpenTelemetryInterceptor.MEMBRANE_OTEL_SPAN;
import static com.predic8.membrane.core.interceptor.opentelemetry.OpenTelemetryInterceptor.MEMBRANE_OTEL_TRACER;
import static java.lang.Long.MAX_VALUE;

/**
 * @description Measures the end-to-end processing time of the child interceptor flow and logs an aligned summary.
 * If an OpenTelemetry parent Span is present on the {@link Exchange}, a child sub-span is created
 * around the measured section so timing data is exported to OTel as well.
 *
 * @example
 * <api port="2000">
 *   <time label="flow-timing">
 *       <!-- plugins to be measured -->
 *   </time>
 * </api>
 *
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "time")
public class TimingInterceptor extends AbstractFlowWithChildrenInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TimingInterceptor.class);

    private final LongAdder callCount = new LongAdder();
    private final LongAdder totalTime = new LongAdder();
    private final AtomicLong minTime = new AtomicLong(MAX_VALUE);
    private final AtomicLong maxTime = new AtomicLong(0);

    private String label;

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        long startNs = System.nanoTime();

        Span parent = exc.getProperty(MEMBRANE_OTEL_SPAN, Span.class);

        Span sub = null;
        Scope parentScope = null;
        Scope subScope = null;

        try {
            if (parent != null) {
                parentScope = parent.makeCurrent();
                sub = getSpan(exc, flow);
                subScope = sub.makeCurrent();
            }

            return switch (flow) {
                case REQUEST -> getFlowController().invokeRequestHandlers(exc, getFlow());
                case RESPONSE -> getFlowController().invokeResponseHandlers(exc, getFlow());
                default -> throw new RuntimeException("Should never happen");
            };
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            updateStats(durationMs);
            long avg = totalTime.sum() / callCount.sum();

            log.info("{} duration: {} ms | calls: {} | min: {} ms | max: {} ms | avg: {} ms",
                    label != null ? label + ": " : "", durationMs, callCount.sum(), minTime.get(), maxTime.get(), avg
            );

            if (sub == null) {
                closeScope(subScope);
                closeScope(parentScope);
            } else {
                endSubspan(sub, subScope, parentScope, flow, durationMs, avg);
            }
        }
    }

    private void updateStats(long durationMs) {
        callCount.increment();
        totalTime.add(durationMs);
        minTime.getAndUpdate(prev -> Math.min(prev, durationMs));
        maxTime.getAndUpdate(prev -> Math.max(prev, durationMs));
    }

    private static Span getSpan(Exchange exc, Flow flow) {
        Tracer tracer = exc.getProperty(MEMBRANE_OTEL_TRACER, Tracer.class);
        if(tracer == null) {
            throw new RuntimeException("OTel tracer not available");
        }
        return tracer.spanBuilder("TimingInterceptor " + flow.name())
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(Context.current())
                .startSpan();
    }

    private void endSubspan(Span sub, Scope subScope, Scope parentScope, Flow flow, long durationMs, long avg) {
        try {
            sub.setAttribute("membrane.interceptor", "TimingInterceptor");
            sub.setAttribute("flow.direction", flow.name());
            sub.setAttribute("time.ms", durationMs);
            sub.setAttribute("time.min.ms", minTime.get());
            sub.setAttribute("time.max.ms", maxTime.get());
            sub.setAttribute("time.avg.ms", avg);
            sub.setAttribute("calls.total", callCount.sum());
        } finally {
            sub.end();
            closeScope(subScope);
            closeScope(parentScope);
        }
    }

    private static void closeScope(Scope s) {
        if (s != null) s.close();
    }

    public String getLabel() {
        return label;
    }

    /**
     * @description Optional label prefix for log lines. Use it to distinguish logs from multiple <time /> blocks.
     * @example "Validation"
     */
    @MCAttribute
    public void setLabel(String label) {
        this.label = label;
    }
}
