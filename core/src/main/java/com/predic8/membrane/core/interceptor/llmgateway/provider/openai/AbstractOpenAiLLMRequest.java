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

package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMRequest;

public abstract class AbstractOpenAiLLMRequest extends AbstractLLMRequest {

    public AbstractOpenAiLLMRequest(Exchange exchange) {
        super(exchange);
    }

    @Override
    public long estimateInputTokens() {

        long chars = countText(json.path("input"));

        chars += estimateChatCompletitions();

        // system instructions
        chars += countText(json.path("system"));

        // tools/functions contribute significantly
        chars += countJsonSize(json.path("tools"));
        chars += countJsonSize(json.path("functions"));

        // safety margin for JSON structure and tokenizer variance
        return Math.max(1, Math.round(chars / 4.0 * 1.15));
    }

    private long estimateChatCompletitions() {
        long chars = 0;
        // Chat Completions API
        var messages = json.path("messages");
        if (messages.isArray()) {
            for (var message : messages) {
                chars += countText(message.path("content"));
                // roles also consume tokens
                chars += message.path("role").asText("").length();
            }
        }
        return chars;
    }

    private long countText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }

        if (node.isTextual()) {
            return node.asText().length();
        }

        if (node.isArray()) {
            long chars = 0;
            for (JsonNode child : node) {
                chars += countText(child);
            }
            return chars;
        }

        if (node.isObject()) {

            // OpenAI content blocks:
            // { "type": "text", "text": "..." }
            long chars = 0;

            var text = node.get("text");
            if (text != null && text.isTextual()) {
                chars += text.asText().length();
            }

            chars += countText(node.get("content"));

            return chars;
        }

        return 0;
    }

    private long countJsonSize(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        return node.toString().length();
    }
}
