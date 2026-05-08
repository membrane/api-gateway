package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AbstractAiApiRequest;

public class ClaudeAiRequest extends AbstractAiApiRequest {

    public static final String X_API_KEY = "x-api-key";

    public ClaudeAiRequest(Exchange exchange) {
        super(exchange);
    }

    @Override
    public long estimateInputTokens() {
        // System prompt
        long tokens = json.path("system").asText().length() / 4;

        // Messages
        for (var message : json.path("messages")) {
            var content = message.path("content");
            if (content.isTextual()) {
                tokens += content.asText().length() / 4;
            } else if (content.isArray()) {
                for (var block : content) {
                    var type = block.path("type").asText();
                    if (type.equals("text")) {
                        tokens += block.path("text").asText().length() / 4;
                    } else if (type.equals("image")) {
                        tokens += 1000;
                    }
                }
            }
        }
        return tokens;
    }

    @Override
    public String getApiKey() {
        return exchange.getRequest().getHeader().getFirstValue(X_API_KEY);
    }

    @Override
    public void setApiKey(String apiKey) {
        exchange.getRequest().getHeader().removeFields(X_API_KEY);
        exchange.getRequest().getHeader().add(X_API_KEY, apiKey);
    }
}
