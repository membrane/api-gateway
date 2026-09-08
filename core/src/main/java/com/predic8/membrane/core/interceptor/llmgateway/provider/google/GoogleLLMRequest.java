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
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractModelInputRequest;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.interceptor.llmgateway.provider.TokenEstimator.charsToTokens;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.TokenEstimator.countText;

public class GoogleLLMRequest extends AbstractModelInputRequest {

    /**
     * x-goog-api-key is correct it is not google
     */
    public static final String X_GOOG_API_KEY = "x-goog-api-key";

    public GoogleLLMRequest(Exchange exchange) throws IOException {
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
    protected String apiKeyHeaderName() {
        return X_GOOG_API_KEY;
    }

    @Override
    protected boolean apiKeyIsBearer() {
        return false;
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

        return charsToTokens(chars);
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
