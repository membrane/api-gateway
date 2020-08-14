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

package com.predic8.membrane.core.interceptor.apimanagement.rateLimiter;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.HashSet;

public class PolicyRateLimit {
    private String name;
    private int requests;
    private Duration interval;
    private DateTime nextCleanup;
    private HashSet<String> services = new HashSet<String>();

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public DateTime getNextCleanup() {
        return nextCleanup;
    }

    public void incrementNextCleanup(){
        nextCleanup = DateTime.now().plus(interval);
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(int requests) {
        this.requests = requests;
    }

    public HashSet<String> getServices() {
        return services;
    }

    public void setServices(HashSet<String> services) {
        this.services = services;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
