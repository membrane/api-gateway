package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbstractFlowWithChildrenInterceptor;
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

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    protected Outcome handleInternal(Exchange exc, Flow flow) {
        long start = System.nanoTime();

        Outcome outcome = switch (flow) {
            case REQUEST -> getFlowController().invokeRequestHandlers(exc, getFlow());
            case RESPONSE -> getFlowController().invokeResponseHandlers(exc, getFlow());
            default -> throw new RuntimeException("Should never happen");
        };

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        callCount.increment();
        totalTime.add(durationMs);
        minTime.getAndUpdate(prev -> Math.min(prev, durationMs));
        maxTime.getAndUpdate(prev -> Math.max(prev, durationMs));

        long avg = totalTime.sum() / callCount.sum();

        log.info("Timing Summary: duration: {} ms | calls: {} | min: {} ms | max: {} ms | avg: {} ms",
                durationMs, callCount.sum(), minTime.get(), maxTime.get(), avg
        );

        return outcome;
    }
}
