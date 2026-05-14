package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.json.JsonUtil;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

public abstract class AbstractLLMRequest implements LLMRequest {

    public static final String BEARER_PREFIX = "Bearer";

    protected final Exchange exchange;
    protected ObjectNode json;

    public AbstractLLMRequest(Exchange exchange) {
        this.exchange = exchange;
        if (exchange.getRequest().isJSON())
            json = JsonUtil.getJsonObject(exchange.getRequest()).orElseThrow(() -> new RuntimeException("No JSON object request."));
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

        int index = ah.indexOf(BEARER_PREFIX);
        if (index < 0) {
            return null;
        }

        var token = ah.substring(index + BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }

    @Override
    public ObjectNode getJson() {
        return json;
    }

    @Override
    public String getModel() {
        return json.path("model").asText();
    }
}
