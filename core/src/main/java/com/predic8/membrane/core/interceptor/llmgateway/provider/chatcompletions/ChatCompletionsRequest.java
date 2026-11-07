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

package com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.AbstractOpenAiLLMRequest;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

public class ChatCompletionsRequest extends AbstractOpenAiLLMRequest {

    private static final Set<String> SYSTEM_ROLES = Set.of("system", "developer");

    private static final List<String> OUTPUT_LIMIT_FIELDS = List.of("max_tokens", "max_completion_tokens");

    public ChatCompletionsRequest(Exchange exchange) throws IOException {
        super(exchange);

        if (json == null) {
            return;
        }

        // Make sure that when streaming is enabled, the usage is included in the response.
        if (json.path("stream").asBoolean(false)) {
            var streamOptions = json.withObject("/stream_options");
            streamOptions.put("include_usage", true);
        }
    }

    /**
     * Lowers every output limit field the client sent to the given maximum, as backends differ in which of
     * {@code max_tokens} and {@code max_completion_tokens} they use. Values below the maximum are kept. Without a
     * client limit, the limit is written to {@link #outputLimitField()}.
     */
    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
        var present = false;
        for (final var field : OUTPUT_LIMIT_FIELDS) {
            if (!json.has(field))
                continue;
            present = true;
            final var requested = json.path(field).asLong(0);
            if (requested <= 0 || requested > maxOutputTokens)
                json.put(field, maxOutputTokens);
        }
        if (!present)
            json.put(outputLimitField(), maxOutputTokens);
    }

    /**
     * @return the field a limit is written to when the client sent none
     */
    protected String outputLimitField() {
        return "max_tokens";
    }

    public List<String> getTools() {
        var tools = getToolsNode();
        if (tools == null)
            return emptyList();
        return tools.valueStream()
                .filter(n -> "function".equals(n.path("type").asText("")))
                .map(n -> n.path("function").path("name").asText(""))
                .filter(name -> !name.isEmpty())
                .toList();
    }

    /**
     * Replaces all system and developer messages with one system message at the start.
     *
     * <p>Chat Completions API wire format:
     * <pre>{@code
     * { "messages": [
     *     {"role": "system", "content": "prompt"},
     *     ...user messages...
     * ]}
     * }</pre>
     */
    @Override
    public void overwriteSystemPrompt(String prompt) {
        removeSystemPrompt();
        json.withArray("messages").insert(0, systemMessage(prompt));
    }

    /**
     * Adds a system message of its own, so the client's system messages stay as they were sent. An
     * empty prompt adds nothing.
     */
    @Override
    public void prependSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        json.withArray("messages").insert(0, systemMessage(prompt));
    }

    /**
     * Adds a system message after the system messages the conversation starts with.
     */
    @Override
    public void appendSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        var messages = json.withArray("messages");
        var index = 0;
        while (index < messages.size() && isSystemMessage(messages.get(index))) {
            index++;
        }
        messages.insert(index, systemMessage(prompt));
    }

    /**
     * Removes all system and developer messages from the {@code "messages"} array. Newer OpenAI
     * models take the system prompt as a developer message.
     * Has no effect if no system message is present.
     */
    @Override
    public void removeSystemPrompt() {
        var messages = json.withArray("messages");
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (isSystemMessage(messages.get(i))) {
                messages.remove(i);
            }
        }
    }

    private static boolean isSystemMessage(JsonNode message) {
        return SYSTEM_ROLES.contains(message.path("role").asText());
    }

    private ObjectNode systemMessage(String prompt) {
        return json.objectNode().put("role", "system").put("content", prompt);
    }

    /**
     * The larger of {@code max_tokens} and {@code max_completion_tokens}, since it is up to the backend which one it
     * uses.
     */
    @Override
    public long getRequestedMaxOutputTokens() {
        return Math.max(json.path("max_completion_tokens").asLong(0), json.path("max_tokens").asLong(0));
    }

}
