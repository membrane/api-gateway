package com.predic8.membrane.core.interceptor.ai.provider.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.AbstractLLMRequest;

public class GoogleLLMRequest extends AbstractLLMRequest {

    /**
     * x-goog-api-key is correct it is not google
     */
    public static final String X_GOOG_API_KEY = "x-goog-api-key";

    public GoogleLLMRequest(Exchange exchange) {
        super(exchange);
    }

    @Override
    public String getModel() {

        var uri = exchange.getRequest().getUri();

        if (uri == null) {
            return null;
        }

        // Example:
        // /v1beta/models/gemini-2.5-pro:generateContent
        int modelsIndex = uri.indexOf("/models/");
        if (modelsIndex < 0) {
            return null;
        }

        var modelPart = uri.substring(modelsIndex + "/models/".length());

        // Support both ':' and URL-encoded '%3A' / '%3a' as separator before the action suffix
        // (e.g. ':generateContent' or '%3AgenerateContent').
        int colonIndex = modelPart.indexOf(':');
        if (colonIndex < 0) {
            colonIndex = modelPart.toLowerCase().indexOf("%3a");
        }
        if (colonIndex >= 0) {
            return modelPart.substring(0, colonIndex);
        }

        return modelPart;
    }

    @Override
    public String getApiKey() {
        return exchange.getRequest().getHeader().getFirstValue(X_GOOG_API_KEY);
    }

    @Override
    public void setApiKey(String apiKey) {
        exchange.getRequest().getHeader().removeFields(X_GOOG_API_KEY);
        exchange.getRequest().getHeader().add(X_GOOG_API_KEY, apiKey);
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        return json.path("generationConfig")
                .path("maxOutputTokens")
                .asLong(0);
    }

    public long estimateInputTokens() {
        if (json == null || json.isNull()) {
            return 0;
        }

        long chars = countText(json.path("systemInstruction"));

        var contents = json.path("contents");
        if (contents.isArray()) {
            for (JsonNode content : contents) {
                chars += countText(content.path("parts"));
            }
        }

        // Safety margin for JSON structure, roles, metadata, etc.
        return Math.max(1, Math.round(chars / 4.0 * 1.15));
    }

    private long countText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }

        if (node.isTextual()) {
            return node.asText().length();
        }

        if (node.isObject()) {
            long chars = 0;

            JsonNode text = node.get("text");
            if (text != null && text.isTextual()) {
                chars += text.asText().length();
            }

            JsonNode parts = node.get("parts");
            if (parts != null) {
                chars += countText(parts);
            }

            return chars;
        }

        if (node.isArray()) {
            long chars = 0;
            for (JsonNode child : node) {
                chars += countText(child);
            }
            return chars;
        }

        return 0;
    }

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
        getGenerationConfig().put("maxOutputTokens", maxOutputTokens);
    }

    private ObjectNode getGenerationConfig() {
        var gc = json.get("generationConfig");
        if (gc instanceof ObjectNode objectNode) {
            return objectNode;
        }
        return json.putObject("generationConfig");
    }
}
