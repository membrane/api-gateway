package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowWithChildrenInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;

@MCElement(name = "time")
public class TimingInterceptor extends AbstractFlowWithChildrenInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TimingInterceptor.class);

    private final LongAdder callCount = new LongAdder();
    private final LongAdder totalTime = new LongAdder();
    private final AtomicLong minTime = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxTime = new AtomicLong(0);

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("MEMBRANE-TIME");

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

        Span parent = exc.getProperty("span", Span.class);
        boolean createSubspan = parent != null;

        Span sub = null;
        Scope parentScope = null;
        Scope subScope = null;

        try {
            if (createSubspan) {
                parentScope = parent.makeCurrent();
                sub = tracer.spanBuilder("TimingInterceptor " + flow.name())
                        .setSpanKind(SpanKind.INTERNAL)
                        .startSpan();
                subScope = sub.makeCurrent();
            }

            return switch (flow) {
                case REQUEST -> getFlowController().invokeRequestHandlers(exc, getFlow());
                case RESPONSE -> getFlowController().invokeResponseHandlers(exc, getFlow());
                default -> throw new RuntimeException("Should never happen");
            };
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;

            callCount.increment();
            totalTime.add(durationMs);
            minTime.getAndUpdate(prev -> Math.min(prev, durationMs));
            maxTime.getAndUpdate(prev -> Math.max(prev, durationMs));
            long calls = callCount.sum();
            long avg = totalTime.sum() / calls;

            log.info("Timing Summary: duration: {} ms | calls: {} | min: {} ms | max: {} ms | avg: {} ms",
                    durationMs, callCount.sum(), minTime.get(), maxTime.get(), avg
            );

            if (sub != null) {
                try {
                    sub.setAttribute("membrane.interceptor", "TimingInterceptor");
                    sub.setAttribute("flow.direction", flow.name());
                    sub.setAttribute("time.ms", durationMs);
                    sub.setAttribute("time.min.ms", minTime.get());
                    sub.setAttribute("time.max.ms", maxTime.get());
                    sub.setAttribute("time.avg.ms", avg);
                    sub.setAttribute("calls.total", calls);
                } finally {
                    sub.end();
                    if (subScope != null) subScope.close();
                    if (parentScope != null) parentScope.close();
                }
            }
        }
    }
}
