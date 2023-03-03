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

package com.predic8.membrane.core.interceptor.apimanagement.quota;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.HashSet;

public class PolicyQuota {

    private String name;
    private long size;
    private Duration interval;
    private DateTime nextCleanup;
    private HashSet<String> services = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public void incrementNextCleanup(){
        setNextCleanup(DateTime.now().plus(interval));
    }

    public HashSet<String> getServices() {
        return services;
    }

    public void setServices(HashSet<String> services) {
        this.services = services;
    }

    public DateTime getNextCleanup() {
        return nextCleanup;
    }

    public void setNextCleanup(DateTime nextCleanup) {
        this.nextCleanup = nextCleanup;
    }
}
