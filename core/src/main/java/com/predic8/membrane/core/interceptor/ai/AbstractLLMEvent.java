package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.util.http.SSEParser;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLLMEvent {

    private static final Logger log = LoggerFactory.getLogger(AbstractLLMEvent.class);

    protected static final ObjectMapper om = new ObjectMapper();

    protected final JsonNode json;

    protected AbstractLLMEvent(JsonNode json) {
        this.json = json;
    }

    public abstract String getType();

    public JsonNode getJson() {
        return json;
    }

    public static AbstractLLMEvent create(SSEParser.SSEEvent sse) {

        if ("[DONE]".equals(sse.data())) {
            return new ChatCompletionDoneEvent();
        }

        var opt = JsonUtil.getJsonObject(sse.data());
        if (opt.isEmpty()) {
            log.info("Unknown event format: {}", sse.data());
        }

        var json = opt.get();

        // Responses API
        if (json.has("type")) {
            return new ResponsesApiEvent(json);
        }

        // Chat Completions API
        if ("chat.completion.chunk".equals(json.path("object").asText())) {
            return new ChatCompletionEvent(json);
        }

        log.debug("Unknown event format: {}", json);

        return null;
    }
}
