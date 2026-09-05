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

package com.predic8.membrane.core.interceptor.llmgateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatCompletionEvent extends AbstractLLMEvent {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionEvent.class);

    public ChatCompletionEvent(JsonNode json) {
        super(json);

        // Walking the choices produces log output and nothing else
        if (log.isDebugEnabled())
            logChoices(json);
    }

    private static void logChoices(JsonNode json) {
        for (JsonNode choice : json.path("choices")) {
            logChoice(choice);
        }
    }

    private static void logChoice(JsonNode choice) {
        var delta = choice.path("delta");

        if (delta.has("content")) {
            log.debug("Content delta: {}", delta.path("content").asText());
        }

        for (JsonNode toolCall : delta.path("tool_calls")) {
            logToolCall(toolCall.path("function"));
        }

        var finishReason = choice.path("finish_reason").asText(null);
        if (finishReason != null && !"null".equals(finishReason)) {
            log.debug("Finish reason: {}", finishReason);
        }
    }

    private static void logToolCall(JsonNode function) {
        if (function.has("name")) {
            log.debug("Tool call name delta: {}", function.path("name").asText());
        }

        if (function.has("arguments")) {
            log.debug("Tool call arguments delta: {}", function.path("arguments").asText());
        }
    }

    @Override
    public String getType() {
        return "chat.completion.chunk";
    }

    public JsonNode getChoices() {
        return json.path("choices");
    }
}
