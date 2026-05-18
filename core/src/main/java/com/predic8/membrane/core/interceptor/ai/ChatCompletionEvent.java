package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatCompletionEvent extends AbstractLLMEvent {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionEvent.class);

    public ChatCompletionEvent(JsonNode json) {
        super(json);

        parseChoices(json);

        var usage = json.path("usage");
        if (!usage.isNull()) {
            var inputTokens = usage.get("prompt_tokens").asInt();
            var outputTokens = usage.get("completion_tokens").asInt();
            var totalTokens = usage.get("total_tokens").asInt();
            System.out.println("------------------------------totalTokens = " + totalTokens);
        }
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
