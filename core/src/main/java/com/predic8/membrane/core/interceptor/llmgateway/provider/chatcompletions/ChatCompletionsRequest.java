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

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
        json.put("max_tokens", maxOutputTokens);
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

    @Override
    public long getRequestedMaxOutputTokens() {
        // Prefer max_completion_tokens (modern OpenAI/o1+), fall back to max_tokens (legacy / all other providers)
        long v = json.path("max_completion_tokens").asLong(0);
        if (v > 0) return v;
        return json.path("max_tokens").asLong(0);
    }

}
