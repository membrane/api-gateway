package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.interceptor.ai.store.Usage;

public class MessageDelta {

    private String stopReason;
    private int inputTokens;
    private int outputTokens;
    private int cacheCreationInputTokens;
    private int cacheReadInputTokens;

    private Usage usage;

    public static MessageDelta from(ObjectNode on) {
        var md = new MessageDelta();

        JsonNode delta = on.path("delta");
        md.stopReason = delta.path("stop_reason").asText(null);

        JsonNode u = on.path("usage");
        if (u.isObject()) {
            md.inputTokens = u.path("input_tokens").asInt(0);
            md.outputTokens = u.path("output_tokens").asInt(0);
            md.cacheCreationInputTokens = u.path("cache_creation_input_tokens").asInt(0);
            md.cacheReadInputTokens = u.path("cache_read_input_tokens").asInt(0);

            // Cache tokens (cache_creation_input_tokens and cache_read_input_tokens) are billable according to Claude's pricing model
            int effectiveInputTokens = md.inputTokens + md.cacheCreationInputTokens + md.cacheReadInputTokens;
            md.usage = new Usage(effectiveInputTokens,md.outputTokens, effectiveInputTokens + md.outputTokens);

        }

        return md;
    }

    public String getStopReason() {
        return stopReason;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getCacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    public int getCacheReadInputTokens() {
        return cacheReadInputTokens;
    }

    public Usage getUsage() {
        return usage;
    }

    @Override
    public String toString() {
        return "MessageDelta{" +
                "stopReason='" + stopReason + '\'' +
                ", inputTokens=" + inputTokens +
                ", outputTokens=" + outputTokens +
                ", cacheCreationInputTokens=" + cacheCreationInputTokens +
                ", cacheReadInputTokens=" + cacheReadInputTokens +
                '}';
    }
}