/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.stats;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.rules.TimeCollector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RuleStatisticCollector {

    /**
     * Map<Status Code, StatisticCollector>
     */
    private ConcurrentHashMap<Integer, StatisticCollector> statusCodes = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, TimeCollector> timeCollector = new ConcurrentHashMap<>();

    private StatisticCollector getStatisticCollectorByStatusCode(int code) {
        StatisticCollector sc = statusCodes.get(code);
        if (sc == null) {
            sc = new StatisticCollector(true);
            StatisticCollector sc2 = statusCodes.putIfAbsent(code, sc);
            if (sc2 != null)
                sc = sc2;
        }
        return sc;
    }

    private TimeCollector getTimeCollectorByStatusCode(int code) {
        code /= 100;

        TimeCollector tc = timeCollector.get(code);

        if (tc == null) {
            tc = new TimeCollector(true);
            TimeCollector tc2 = timeCollector.putIfAbsent(code, tc);
            if (tc2 != null)
                tc = tc2;
        }

        return tc;
    }

    public Map<Integer, StatisticCollector> getStatisticsByStatusCodes() {
        return statusCodes;
    }

    public Map<Integer, TimeCollector> getTimeStatisticsByStatusCodeRange() {
        return timeCollector;
    }

    public void collect(Exchange exc) {
        StatisticCollector sc = getStatisticCollectorByStatusCode(exc
                .getResponse().getStatusCode());
        synchronized (sc) {
            sc.collectFrom(exc);
        }

        TimeCollector tc = getTimeCollectorByStatusCode(exc
                .getResponse().getStatusCode());
        synchronized (tc) {
            tc.collectFrom(exc);
        }
    }

    public int getCount() {
        int c = 0;
        for (StatisticCollector statisticCollector : statusCodes.values()) {
            c += statisticCollector.getCount();
        }
        return c;
    }
}
