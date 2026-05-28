/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway.provider.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMRequest;

import java.util.List;

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

    /**
     * Returns the text of the first part inside {@code systemInstruction},
     * or an empty string if no system prompt is set.
     *
     * <p>Gemini API wire format:
     * <pre>{@code
     * { "systemInstruction": { "parts": [{ "text": "You are a helpful assistant." }] } }
     * }</pre>
     */
    @Override
    public String getSystemPrompt() {
        for (var part : json.path("systemInstruction").path("parts")) {
            if (part.path("text").isTextual()) {
                return part.path("text").asText("");
            }
        }
        return "";
    }

    /**
     * Concatenates all prompts (newline-separated) into a single text part under
     * {@code systemInstruction}. Replaces any existing system instruction.
     *
     * <p>Gemini API wire format:
     * <pre>{@code { "systemInstruction": { "parts": [{ "text": "prompt 1\nprompt 2" }] } }}</pre>
     */
    @Override
    public void setSystemPrompts(List<String> prompts) {
        json.putObject("systemInstruction")
                .putArray("parts")
                .addObject()
                .put("text", String.join("\n", prompts));
    }

    /**
     * Removes the {@code systemInstruction} field entirely.
     * Has no effect if no system instruction is present.
     */
    @Override
    public void removeSystemPrompt() {
        json.remove("systemInstruction");
    }

    @Override
    public boolean isChatCompletion() {
        // Gemini uses its own generateContent API, not Chat Completions
        return false;
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
