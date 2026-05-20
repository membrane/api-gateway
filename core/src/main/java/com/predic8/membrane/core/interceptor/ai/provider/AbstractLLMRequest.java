package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

public abstract class AbstractLLMRequest extends AbstractLLMMessage implements LLMRequest {

    private static final Logger log = LoggerFactory.getLogger(AbstractLLMRequest.class);

    public static final String BEARER_PREFIX = "Bearer";

    protected ObjectNode json;

    public AbstractLLMRequest(Exchange exchange) {
        super(exchange);

        if (exchange.getRequest().isJSON()) {
            json = JsonUtil.getJsonObject(exchange.getRequest()).orElseThrow(() -> new RuntimeException("Cannot parse input as JSON message."));
        } else {
            log.info("Request is not JSON:");
            throw new RuntimeException("Request is not JSON.");
        }
    }

    public List<String> getTools() {
       return Collections.emptyList();
    }

    protected ArrayNode getToolsNode() {
        if (json == null)
            return null;
        if (json.path("tools").isArray())
            return (ArrayNode) json.path("tools");
        return null;
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
