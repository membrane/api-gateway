package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.predic8.membrane.core.exchange.Exchange;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

public class BaseLLMRequest extends AbstractLLMMessage implements LLMRequest {

    public static final String BEARER_PREFIX = "Bearer";

    protected BaseLLMRequest(Exchange exchange) {
        super(exchange);
    }

    @Override
    public void setApiKey(String apiKey) {
        exchange.getRequest().getHeader().removeFields(AUTHORIZATION);
        exchange.getRequest().getHeader().add(AUTHORIZATION, "Bearer " + apiKey);
    }

    @Override
    public String getApiKey() {
        var ah = exchange.getRequest().getHeader().getAuthorization();
        if (ah == null) {
            return null;
        }

        if (!ah.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        var token = ah.substring(BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }

}
