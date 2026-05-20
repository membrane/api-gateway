package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "users", component = false, id="ai-api-users")
public class AiApiUser {

    private String name;
    private String apiKey;

    private long tokens;

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
