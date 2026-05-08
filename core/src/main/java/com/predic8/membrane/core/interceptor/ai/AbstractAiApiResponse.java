package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.store.Usage;
import com.predic8.membrane.core.util.json.JsonUtil;

public abstract class AbstractAiApiResponse implements AiApiResponse {

    protected final Exchange exchange;
    protected ObjectNode json;

    public AbstractAiApiResponse(Exchange exchange) {
        this.exchange = exchange;
        json = JsonUtil.getJsonObject(exchange.getResponse()).orElse(JsonNodeFactory.instance.objectNode().put("error", "No JSON object response from model."));
    }

    @Override
    public boolean isError() {
        return json.get("error") != null && !json.get("error").isNull();
    }

    public Usage getUsage() {

        var usage = json.path("usage");

        int inputTokens = getInputTokens(usage);
        int outputTokens = getOutputTokens(usage);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);

        return new Usage(
                inputTokens,
                outputTokens,
                totalTokens
        );
    }

    private static int getOutputTokens(JsonNode usage) {
        return usage.path("output_tokens").asInt(
                usage.path("completion_tokens").asInt(0)
        );
    }

    private static int getInputTokens(JsonNode usage) {
        return usage.path("input_tokens").asInt(
                usage.path("prompt_tokens").asInt(0));
    }
}
