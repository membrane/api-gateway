/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangeState;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimeCollector {

    private final boolean countErrorExchanges;

    private static List<Long> buckets = Stream.of(500L, 1000L, 2000L, 4000L, 10_000L)
            .collect(Collectors.toList());

    private Map<String, Map<String, Long>> trackedTimes;
    private Map<String, Long> membraneReqProcess;
    private Map<String, Long> membraneResProcess;
    private Map<String, Long> responseProcess;
    private Map<String, Long> totalTimeProcess;

    public TimeCollector(boolean countErrorExchanges) {
        this.countErrorExchanges = countErrorExchanges;

        trackedTimes = new HashMap<>();
        membraneReqProcess = new HashMap<>();
        membraneReqProcess = new HashMap<>();
        membraneResProcess = new HashMap<>();
        totalTimeProcess = new HashMap<>();

        responseProcess = new HashMap<>();
        trackedTimes.put("process_req_time", membraneReqProcess);
        trackedTimes.put("process_res_time", membraneResProcess);
        trackedTimes.put("response_time", responseProcess);
        trackedTimes.put("total_time", totalTimeProcess);

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


        long totalTime = timeMembraneReqProcess + timeResponseProcess + timeMembraneResProcess;
        addToBucket(totalTime, totalTimeProcess);
    }

    private void addToBucket(long value, Map<String, Long> buckets) {
        buckets.merge("SUM", value, Long::sum);
        buckets.merge("COUNT", 1L, Long::sum);

        for (long bucket : TimeCollector.buckets) {
            if (value <= bucket) {
                buckets.merge(String.valueOf(bucket), 1L, Long::sum);
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
