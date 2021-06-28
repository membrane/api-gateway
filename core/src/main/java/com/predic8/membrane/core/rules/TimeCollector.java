package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangeState;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimeCollector {

    private final boolean countErrorExchanges;

    private static List<Long> buckets = new ArrayList<>();

    private Map<String, Map<String, Long>> trackedTimes;
    private Map<String, Long> membraneReqProcess;
    private Map<String, Long> membraneResProcess;
    private Map<String, Long> responseProcess;

    public TimeCollector(boolean countErrorExchanges) {
        this.countErrorExchanges = countErrorExchanges;

        trackedTimes = new HashMap<>();
        membraneReqProcess = new HashMap<>();
        membraneReqProcess = new HashMap<>();
        membraneResProcess = new HashMap<>();

        responseProcess = new HashMap<>();
        trackedTimes.put("process_req_time", membraneReqProcess);
        trackedTimes.put("process_res_time", membraneResProcess);
        trackedTimes.put("response_time", responseProcess);

        handleEmptyBuckets();
    }

    private void handleEmptyBuckets() {
        if (!buckets.isEmpty())
            return;

        TimeCollector.buckets =
                Stream.of(500L, 1000L, 2000L, 4000L, 10_000L)
                        .collect(Collectors.toList());
    }

    public Map<String, Map<String, Long>> getTrackedTimes() {
        return trackedTimes;
    }

    public void collectFrom(AbstractExchange exc) {
        if (exc.getStatus() == ExchangeState.FAILED && !countErrorExchanges)
            return;

        if (exc.getTimeReqSent() == 0)
            return; // exchange did not reach HTTPClientInterceptor

        if (exc.getTimeResSent() == 0)
            return; // exchange is not yet completed

        // 2-1
        long timeMembraneReqProcess = exc.getTimeReqSent() - exc.getTimeReqReceived();
        addToBucket(timeMembraneReqProcess, membraneReqProcess);

        if (exc.getTimeResReceived() == 0)
            return;

        // 3-2
        long timeResponseProcess = exc.getTimeResReceived() - exc.getTimeReqSent();
        addToBucket(timeResponseProcess, responseProcess);

        // 4-3
        long timeMembraneResProcess = exc.getTimeResSent() - exc.getTimeResReceived();
        addToBucket(timeMembraneResProcess, membraneResProcess);
    }

    private void addToBucket(long value, Map<String, Long> buckets) {
        buckets.merge("SUM", value, Long::sum);
        buckets.merge("COUNT", 1L, Long::sum);

        for (long bucket : TimeCollector.buckets) {
            if (value <= bucket) {
                buckets.merge(String.valueOf(bucket), 1L, Long::sum);
                return;
            }
        }
        buckets.merge("+Inf", 1L, Long::sum);
    }

    public static List<Long> getBuckets() {
        return buckets;
    }

    public static void setBuckets(List<Long> buckets) {
        TimeCollector.buckets = buckets;
    }
}
