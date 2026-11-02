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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractModelInputRequest;

import java.io.IOException;

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
     * Replaces any existing system instruction with one text part.
     *
     * <p>Gemini API wire format:
     * <pre>{@code { "systemInstruction": { "parts": [{ "text": "prompt" }] } }}</pre>
     */
    @Override
    public void overwriteSystemPrompt(String prompt) {
        json.putObject("systemInstruction")
                .putArray("parts")
                .add(textPart(prompt));
    }

    /**
     * An empty prompt leaves the client's system instruction as it was sent: Gemini rejects empty parts.
     */
    @Override
    public void prependSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        systemParts().insert(0, textPart(prompt));
    }

    @Override
    public void appendSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        systemParts().add(textPart(prompt));
    }

    /**
     * The parts of the client's system instruction, created if there is none.
     */
    private ArrayNode systemParts() {
        var instruction = json.get("systemInstruction") instanceof ObjectNode existing ? existing : json.putObject("systemInstruction");
        return instruction.get("parts") instanceof ArrayNode parts ? parts : instruction.putArray("parts");
    }

    private ObjectNode textPart(String text) {
        return json.objectNode().put("text", text);
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
