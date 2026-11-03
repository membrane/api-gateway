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

package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractModelInputRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.predic8.membrane.core.http.Header.ACCEPT_ENCODING;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.TokenEstimator.*;

/**
 * system field for system prompt
 */
public class ClaudeLLMRequest extends AbstractModelInputRequest {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLLMRequest.class);

    public static final String X_API_KEY = "x-api-key";

    private static final String IDENTITY = "identity";

    /**
     * What an image or a document of unknown size counts for: about 1000 tokens.
     */
    private static final long FLAT_BLOCK_CHARS = 4000;

    public ClaudeLLMRequest(Exchange exchange) throws IOException {
        super(exchange);

        askForAnUncompressedResponse();
    }

    /**
     * The streamed response is parsed as SSE text chunk by chunk, which only works while it is not
     * compressed, so the gateway asks Anthropic not to encode it.
     */
    private void askForAnUncompressedResponse() {
        exchange.getRequest().getHeader().setValue(ACCEPT_ENCODING, IDENTITY);
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

    /**
     * Counts everything Anthropic bills as input: the system prompt as text or as text blocks, the
     * tool definitions and every content block of the messages, including tool calls and their results.
     */
    @Override
    public long estimateInputTokens() {
        var chars = countText(json.path("system")) + countJsonSize(json.path("tools"));
        for (var message : json.path("messages")) {
            chars += countContent(message.path("content"));
        }
        return charsToTokens(chars);
    }

    private static long countContent(JsonNode content) {
        if (!content.isArray())
            return countText(content);
        long chars = 0;
        for (var block : content) {
            chars += countBlock(block);
        }
        return chars;
    }

    private static long countBlock(JsonNode block) {
        return switch (block.path("type").asText()) {
            case "image" -> FLAT_BLOCK_CHARS;
            case "document" -> countText(block.path("title")) + countText(block.path("context")) + countDocument(block.path("source"));
            case "tool_use" -> countJsonSize(block.path("input"));
            case "tool_result" -> countContent(block.path("content"));
            case "thinking" -> countText(block.path("thinking"));
            default -> countText(block);
        };
    }

    /**
     * A PDF is billed by its pages, which cannot be told from the request, so it gets the same flat
     * allowance as an image.
     */
    private static long countDocument(JsonNode source) {
        return switch (source.path("type").asText()) {
            case "text" -> countText(source.path("data"));
            case "content" -> countContent(source.path("content"));
            default -> FLAT_BLOCK_CHARS;
        };
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
    public long getRequestedMaxOutputTokens() {
        return json.path("max_tokens").asLong(0);
    }

    @Override
    protected String apiKeyHeaderName() {
        return X_API_KEY;
    }

    @Override
    protected boolean apiKeyIsBearer() {
        return false;
    }

    @Override
    public void overwriteSystemPrompt(String prompt) {
        json.put("system", prompt);
    }

    /**
     * An empty prompt leaves the client's system prompt as it was sent: Anthropic rejects empty text blocks.
     */
    @Override
    public void prependSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        systemBlocks().insert(0, textBlock(prompt));
    }

    /**
     * An empty prompt leaves the client's system prompt as it was sent: Anthropic rejects empty text blocks.
     */
    @Override
    public void appendSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        systemBlocks().add(textBlock(prompt));
    }

    /**
     * The {@code "system"} field as text blocks, so that a prompt can be added next to the client's
     * blocks without touching them and their {@code cache_control}. A string is turned into one block.
     */
    private ArrayNode systemBlocks() {
        var system = json.get("system");
        if (system instanceof ArrayNode blocks) {
            return blocks;
        }
        var blocks = json.putArray("system");
        if (system != null && system.isTextual() && !system.asText().isEmpty()) {
            blocks.add(textBlock(system.asText()));
        }
        return blocks;
    }

    private ObjectNode textBlock(String text) {
        return json.objectNode().put("type", "text").put("text", text);
    }

    /**
     * Removes the top-level {@code "system"} field entirely.
     * Has no effect if no system prompt is present.
     */
    @Override
    public void removeSystemPrompt() {
        json.remove("system");
    }
}
