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

package com.predic8.membrane.core.interceptor.apimanagement;

import com.predic8.membrane.core.interceptor.apimanagement.policy.Policy;

import java.time.Instant;
import java.util.HashSet;

public class Key {
    private String name = "";
    private Instant expiration;
    private HashSet<Policy> policies = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HashSet<Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(HashSet<Policy> policies) {
        this.policies = policies;
    }

    public String toString(){
        StringBuilder builder = new StringBuilder();
        builder.append("Key: ").append(name).append("|");
        int c = 1;
        for(Policy p : policies){
            builder.append(" [").append(c++).append("] ").append(p.getName());
        }
        return builder.toString();
    }

    public Instant getExpiration() {
        return expiration;
    }

    public void setExpiration(Instant expiration) {
        this.expiration = expiration;
    }
}
