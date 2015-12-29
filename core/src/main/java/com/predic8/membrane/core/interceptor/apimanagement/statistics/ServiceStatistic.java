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

import java.util.concurrent.ConcurrentHashMap;

public class ServiceStatistic {
    private String name;
    private ConcurrentHashMap<String,PathStatistic> statisticsForPath = new ConcurrentHashMap<String, PathStatistic>();

    public ServiceStatistic(String serviceName) {
        this.setName(serviceName);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConcurrentHashMap<String, PathStatistic> getStatisticsForPath() {
        return statisticsForPath;
    }

    public void setStatisticsForPath(ConcurrentHashMap<String, PathStatistic> statisticsForPath) {
        this.statisticsForPath = statisticsForPath;
    }
}
