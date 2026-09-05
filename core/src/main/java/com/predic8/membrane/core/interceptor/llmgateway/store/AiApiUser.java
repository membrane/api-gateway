/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Long.MAX_VALUE;

@MCElement(name = "users", component = false, id = "ai-api-users")
public class AiApiUser {

    private String name;
    private String apiKey;

    private long tokens = 0;

    private final AtomicLong tokensUsedInPeriod = new AtomicLong();

    /**
     * Updates the store with the number of tokens used in this call
     *
     * @param usage The number of tokens used
     */
    public void addTokensUsedInPeriod(Usage usage) {
        tokensUsedInPeriod.addAndGet(usage.totalTokens());
    }

    public void resetTokensUsedInPeriod() {
        tokensUsedInPeriod.set(0);
    }

    /**
     * Checks if the user has enough tokens to make the request.
     * <p>
     * The check is advisory: it reads the usage of the period without reserving anything, so
     * requests running at the same time can each be told there is budget left and together overshoot
     * it. What they actually used is settled by {@link #addTokensUsedInPeriod(Usage)} once the
     * responses arrive, and the next request sees it.
     *
     * @param tokensNeededForRequest The number of tokens that the user needs to make the request
     * @return The estimated number of tokens that the user has left after this request
     */
    public long checkLimit(long tokensNeededForRequest) {
        if (tokens == 0)
            return MAX_VALUE;
        return this.tokens - tokensUsedInPeriod.get() - tokensNeededForRequest;
    }

    public String getName() {
        return name;
    }

    /**
     * @description Name of the api user, group or cost center. Used to attribute the recorded usage.
     * @example alice
     */
    @MCAttribute()
    public void setName(String name) {
        this.name = name;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * @description The api key the client sends to authenticate as this user. It is the key of the gateway, not the key
     * of the provider, and it is required.
     * @example abc123
     */
    @MCAttribute()
    public void setApiKey(String apikey) {
        this.apiKey = apikey;
    }


    public long getTokens() {
        return tokens;
    }

    /**
     * @description Number of tokens the user may spend within the current period. Input and the reserved output of a
     * request count against it, and the period is set by <code>limitResetPeriod</code> of the store.
     * @default 0 (no limit)
     * @example 10000
     */
    @MCAttribute
    public void setTokens(long tokens) {
        if (tokens < 0) {
            throw new ConfigurationException("tokens must be >= 0");
        }
        this.tokens = tokens;
    }

    @Override
    public String toString() {
        return "user(name: %s)".formatted(name);
    }
}
