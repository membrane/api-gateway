package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponsesApiEvent extends AbstractLLMEvent {

    private static final Logger log = LoggerFactory.getLogger(ResponsesApiEvent.class);

    private final String type;

    public ResponsesApiEvent(JsonNode json) {
        super(json);

        this.type = json.path("type").asText();

        log.debug("Responses API event: {}", type);

        if ("response.output_item.done".equals(type)) {

            var item = json.path("item");

            if (item.isObject()) {
                var on = (ObjectNode) item;

                if ("function_call".equals(on.path("type").asText())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Function call: {} with params {}",
                                on.path("name").asText(),
                                on.path("arguments").asText());
                    } else {
                        log.info("Function call: {}", on.path("name"));
                    }
                }
            }
        }
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ResponsesApiEvent{" +
                "type='" + type + '\'' +
                '}';
    }
}
