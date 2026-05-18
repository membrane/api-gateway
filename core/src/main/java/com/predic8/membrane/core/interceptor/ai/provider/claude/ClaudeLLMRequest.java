package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.AbstractLLMRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaudeLLMRequest extends AbstractLLMRequest {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLLMRequest.class);

    public static final String X_API_KEY = "x-api-key";

    public ClaudeLLMRequest(Exchange exchange) {
        super(exchange);

        exchange.getRequest().getHeader().setValue( "Accept-Encoding","identity");
    }

    public void setMaxOutputTokens(int maxOutputTokens) {

        // Thinking needs a certain number of tokens
        if (maxOutputTokens < 2048 && isThinking()) {
            log.info("maxOutputTokens is {}. Too low for thinking. Disabling thinking.", maxOutputTokens);
            disableThinking();
        }

        json.put("max_tokens", maxOutputTokens);

        if (isThinking()) {
            var thinking = (ObjectNode) json.path("thinking");
            if (!thinking.path("budget_tokens").isNull()) {
                var budgetTokens = thinking.path("budget_tokens").asInt();
                if (budgetTokens >= maxOutputTokens) {
                    // budget_tokens must be smaller than max_tokens
                    // value might vary between models
                    thinking.put("budget_tokens", Math.min(maxOutputTokens / 2, 1024));
                }
            }

        }
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

    private boolean isThinking() {
        var thinking = json.path("thinking");
        return thinking.isObject() && "enabled".equals(thinking.path("type").asText());
    }

    private void disableThinking() {
        var thinking = json.putObject("thinking");
        thinking.put("type", "disabled");
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
