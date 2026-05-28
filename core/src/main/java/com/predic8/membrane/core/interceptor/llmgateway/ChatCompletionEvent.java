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

        parseChoices(json);

    }


    private static void parseChoices(JsonNode json) {
        for (JsonNode choice : json.path("choices")) {

            JsonNode delta = choice.path("delta");

            if (delta.has("content")) {
                log.debug("Content delta: {}",
                        delta.path("content").asText());
            }

            if (delta.has("tool_calls")) {

                for (JsonNode tc : delta.path("tool_calls")) {

                    JsonNode fn = tc.path("function");

                    if (fn.has("name")) {
                        log.debug("Tool call name delta: {}",
                                fn.path("name").asText());
                    }

                    if (fn.has("arguments")) {
                        log.debug("Tool call arguments delta: {}",
                                fn.path("arguments").asText());
                    }
                }
            }

            String finishReason = choice.path("finish_reason").asText(null);

            if (finishReason != null && !"null".equals(finishReason)) {
                log.debug("Finish reason: {}", finishReason);
            }
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
