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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.predic8.membrane.core.exchange.Exchange;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

public class OpenAiLLMResponsesRequest extends AbstractOpenAiLLMRequest {

    private static final Set<String> SYSTEM_ROLES = Set.of("system", "developer");

    public OpenAiLLMResponsesRequest(Exchange exchange) throws IOException {
        super(exchange);
    }

    public List<String> getTools() {
        var tools = getToolsNode();
        if (tools == null)
            return emptyList();
        return tools.valueStream()
                .filter(n -> "function".equals(n.path("type").asText("")))
                .map(n -> n.path("name").asText(""))
                .filter(name -> !name.isEmpty())
                .toList();
    }

    @Override
    public void overwriteSystemPrompt(String prompt) {
        removeSystemPrompt();
        json.put("instructions", prompt);
    }

    /**
     * {@code "instructions"} is a plain string, so both prompts are joined with a newline. An empty
     * prompt leaves it as the client sent it.
     *
     * <p>OpenAI Responses API wire format:
     * <pre>{@code { "instructions": "prompt 1\nprompt 2", "input": "..." }}</pre>
     */
    @Override
    public void prependSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        json.put("instructions", joinLines(prompt, json.path("instructions").asText("")));
    }

    @Override
    public void appendSystemPrompt(String prompt) {
        if (prompt.isEmpty())
            return;
        json.put("instructions", joinLines(json.path("instructions").asText(""), prompt));
    }

    private static String joinLines(String first, String second) {
        if (first.isEmpty())
            return second;
        if (second.isEmpty())
            return first;
        return first + "\n" + second;
    }

    /**
     * Removes the {@code "instructions"} field and the system and developer messages in {@code "input"}, which act as
     * system prompts as well.
     * Has no effect if no system prompt is present.
     */
    @Override
    public void removeSystemPrompt() {
        json.remove("instructions");
        if (!(json.get("input") instanceof ArrayNode input))
            return;
        for (int i = input.size() - 1; i >= 0; i--) {
            if (SYSTEM_ROLES.contains(input.get(i).path("role").asText()))
                input.remove(i);
        }
    }

    @Override
    public long getRequestedMaxOutputTokens() {
        return json.path("max_output_tokens").asLong(0);
    }

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
        json.put("max_output_tokens", maxOutputTokens);
    }
}
