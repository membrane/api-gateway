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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.AbstractOpenAiLLMRequest;

import java.util.List;

import static java.util.Collections.emptyList;

public class ChatCompletionsRequest extends AbstractOpenAiLLMRequest {

    public ChatCompletionsRequest(Exchange exchange) {
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
     * Returns the content of the first {@code "role": "system"} message,
     * or an empty string if none is present.
     */
    @Override
    public String getSystemPrompt() {
        for (var message : json.path("messages")) {
            if ("system".equals(message.path("role").asText())) {
                return message.path("content").asText("");
            }
        }
        return "";
    }

    /**
     * Replaces all system messages with one separate {@code {"role":"system","content":"..."}} message
     * per prompt, prepended to the messages array in list order.
     *
     * <p>Chat Completions API wire format:
     * <pre>{@code
     * { "messages": [
     *     {"role": "system", "content": "prompt 1"},
     *     {"role": "system", "content": "prompt 2"},
     *     ...user messages...
     * ]}
     * }</pre>
     */
    @Override
    public void setSystemPrompts(List<String> prompts) {
        removeSystemPrompt();
        var messages = json.withArray("messages");
        // Insert in reverse so that prompts[0] ends up at index 0
        for (int i = prompts.size() - 1; i >= 0; i--) {
            var systemMessage = json.objectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", prompts.get(i));
            messages.insert(0, systemMessage);
        }
    }

    /**
     * Removes all {@code "role": "system"} messages from the {@code "messages"} array.
     * Has no effect if no system message is present.
     */
    @Override
    public void removeSystemPrompt() {
        var messages = json.withArray("messages");
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("system".equals(messages.get(i).path("role").asText())) {
                messages.remove(i);
            }
        }
    }

    @Override
    public boolean isChatCompletion() {
        return true;
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        // Prefer max_completion_tokens (modern OpenAI/o1+), fall back to max_tokens (legacy / all other providers)
        long v = json.path("max_completion_tokens").asLong(0);
        if (v > 0) return v;
        return json.path("max_tokens").asLong(0);
    }

}
