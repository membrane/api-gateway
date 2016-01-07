/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.apimanagement.statistics;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.AbstractMessageObserver;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AMStatisticsCollector {

    int bodyBytes = -1;
    ConcurrentHashMap<String,ApiKeyStatistic> statisticsForKey = new ConcurrentHashMap<String, ApiKeyStatistic>();
    ConcurrentLinkedQueue<Exchange> exchangesToCollect = new ConcurrentLinkedQueue<Exchange>();

    ExecutorService collectorThread = Executors.newFixedThreadPool(1);

    /**
     * Inits listeners and collects statistik from the exchange
     * @param exc
     * @param outcome
     * @return always returns the outcome unmodified
     */
    public Outcome handleRequest(final Exchange exc, final Outcome outcome){
        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
            @Override
            public void setExchangeFinished() {
                exchangesToCollect.add(exc);
            }
        });

        /*collectorThread.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    for(Exchange excQ : exch)
                    // SO 3752194: tries to prevent further unneeded allocations when aks already exists
                    ApiKeyStatistic aks = statisticsForKey.get(apiKey);
                    if (aks == null) {
                        ApiKeyStatistic newValue = new ApiKeyStatistic(apiKey, bodyBytes);
                        aks = statisticsForKey.putIfAbsent(apiKey, newValue);
                        if (aks == null) {
                            aks = newValue;
                        }
                    }
                    //

                    aks.collectFrom(exc);
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        });*/

        /*exc.getRequest().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody body) {
                //statistics.addRequestBody(exc, bodyBytes);
            }
        });*/



        return outcome;
    }

    public Outcome handleResponse(Exchange exc, Outcome outcome) {
        /*exc.getResponse().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody body) {
                //statistics.addResponseBody(exc,bodyBytes);
            }
        });*/

        return outcome;
    }
}
