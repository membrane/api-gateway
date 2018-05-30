package com.predic8.membrane.core.stats;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.StatisticCollector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RuleStatisticCollector {

    /**
     * Map<Status Code, StatisticCollector>
     */
    private ConcurrentHashMap<Integer, StatisticCollector> statusCodes = new ConcurrentHashMap<Integer, StatisticCollector>();

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

    public Map<Integer, StatisticCollector> getStatisticsByStatusCodes() {
        return statusCodes;
    }

    public void collect(Exchange exc) {
        StatisticCollector sc = getStatisticCollectorByStatusCode(exc
                .getResponse().getStatusCode());
        synchronized (sc) {
            sc.collectFrom(exc);
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
