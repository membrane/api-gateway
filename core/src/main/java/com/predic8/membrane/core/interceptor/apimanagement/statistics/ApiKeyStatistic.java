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

import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.StatisticCollector;

import java.util.concurrent.ConcurrentHashMap;

public class ApiKeyStatistic {
    String apiKey;
    /**
     * The key is the name of the service
     */
    ConcurrentHashMap<String, ServiceStatistic> statisticsForService = new ConcurrentHashMap<String, ServiceStatistic>();

    public ApiKeyStatistic(String apiKey){
        this.apiKey = apiKey;
    }

    public void collectFrom(Exchange exc) {
        StatisticCollector collector = new StatisticCollector(false);
        collector.collectFrom(exc);

        String serviceName = exc.getRule().getName();
        ServiceStatistic serviceSta = statisticsForService.get(serviceName);
        if(serviceSta == null){
            ServiceStatistic newValue = new ServiceStatistic(serviceName);
            serviceSta = statisticsForService.putIfAbsent(serviceName,newValue);
            if(serviceSta == null)
                serviceSta = newValue;
        }
        Path path = ((ServiceProxy)exc.getRule()).getPath();
        PathStatistic pathSta = serviceSta.getStatisticsForPath().get(path.getValue());
        if(pathSta == null){
            PathStatistic newValue = new PathStatistic(path.getValue());
            pathSta = serviceSta.getStatisticsForPath().putIfAbsent(path.getValue(),newValue);
            if(pathSta == null)
                pathSta = newValue;
        }
        synchronized(pathSta) {
            pathSta.getStatistics().collectFrom(collector);
        }

    }

    public void addRequestBody(Exchange exc, int bodyBytes) {
    }

    public void addResponseBody(Exchange exc, int bodyBytes) {
    }
}
