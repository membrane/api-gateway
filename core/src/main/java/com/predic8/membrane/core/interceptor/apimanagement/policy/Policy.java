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

package com.predic8.membrane.core.interceptor.apimanagement.policy;

import java.util.HashSet;

public class Policy
{
    private String name = "";
    private RateLimit rateLimit = new RateLimit();
    private Quota quota = new Quota();
    private HashSet<String> serviceProxies = new HashSet<String>();

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public HashSet<String> getServiceProxies() {
        return serviceProxies;
    }

    public void setServiceProxies(HashSet<String> serviceProxies) {
        this.serviceProxies = serviceProxies;
    }

    public String toString(){
        StringBuilder builder = new StringBuilder();
        builder.append("Policy: " ).append(name).append("| ").append("Services: ");
        int c = 1;
        for(String sp : serviceProxies){
            builder.append("[").append(c++).append("] ").append(sp).append(" ");
        }
        return builder.toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public Quota getQuota() {
        return quota;
    }

    public void setQuota(Quota quota) {
        this.quota = quota;
    }
}
