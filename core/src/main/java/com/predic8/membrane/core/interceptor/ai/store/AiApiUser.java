package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import java.util.concurrent.atomic.AtomicLong;

@MCElement(name = "users", component = false, id="ai-api-users")
public class AiApiUser {

    private String name;
    private String apiKey;

    private long tokens;

    private final AtomicLong tokensUsedInPeriod = new AtomicLong();

    /**
     * Updates the store with the number of tokens used in this call
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
     * @param tokensNeededForRequest The number of tokens that the user needs to make the request
     * @return The estimated number of tokens that the user has left after this request
     */
    public long checkLimit(long tokensNeededForRequest) {
        return this.tokens - tokensUsedInPeriod.get() - tokensNeededForRequest;
    }

    public String getName() {
        return name;
    }

    /**
     * @description Name of the API user, group or cost center.
     * @param name of the user
     */
    @MCAttribute()
    public void setName(String name) {
        this.name = name;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * @description API key to authenticate the user at the llm gateway
     * @default (not set)
     * @param apikey to authenticate the user
     */
    @MCAttribute()
    public void setApiKey(String apikey) {
        this.apiKey = apikey;
    }


    public long getTokens() {
        return tokens;
    }

    /**
     * @description Number of tokens that the user has available within the current period.
     * @default 0 (no limit)
     * @param tokens available tokens
     */
    @MCAttribute
    public void setTokens(long tokens) {
        this.tokens = tokens;
    }

    @Override
    public String toString() {
        return "user(name: %s)".formatted(name);
    }
}
