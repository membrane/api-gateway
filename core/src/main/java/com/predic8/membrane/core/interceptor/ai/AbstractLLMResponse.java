package com.predic8.membrane.core.interceptor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractMessageObserver;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.interceptor.ai.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public abstract class AbstractLLMResponse implements LLMResponse {

    private static final Logger log = LoggerFactory.getLogger(AbstractLLMResponse.class);

    protected final Exchange exchange;
    protected ObjectNode json;
    Consumer<LLMResponse> postProcessor;

    public AbstractLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        this.exchange = exchange;
        this.postProcessor = postProcessor;
        var msg = exchange.getResponse();

        if (msg.isStream()) {

            var parser = new SSEParser("response.completed","response.incompleted");

            msg.getBody().addObserver(new AbstractMessageObserver() {
                @Override
                public void bodyChunk(Chunk chunk) {
                    if (!parser.parse(chunk)) {
                        return;
                    }

                    var events = parser.getEvents();
                    var terminal = parser.getTerminalEvent();

                    log.debug("---------------------------------------------------------------");
                    log.debug("Events: {}", events.size());
                    events.forEach(e -> log.debug("Event: {}", e));
                    log.debug("---------------------------------------------------------------");


                    terminal.ifPresent(event -> {
                        json = JsonUtil.getJsonObject(event.data())
                                .orElse(JsonNodeFactory.instance.objectNode()
                                        .put("error", "No JSON object response from model."));

                        postProcessor.accept(AbstractLLMResponse.this);
                    });
                }
            });
        } else {
            json = JsonUtil.getJsonObject(exchange.getResponse())
                    .orElse(JsonNodeFactory.instance.objectNode().put("error", "No JSON object response from model."));
        }
    }

    @Override
    public boolean isError() {
        return json.get("error") != null && !json.get("error").isNull();
    }

    public Usage getUsage() {

        var usage = json.path("usage");

        int inputTokens = getInputTokens(usage);
        int outputTokens = getOutputTokens(usage);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);

        return new Usage(
                inputTokens,
                outputTokens,
                totalTokens
        );
    }

    protected static int getOutputTokens(JsonNode usage) {
        return usage.path("output_tokens").asInt(
                usage.path("completion_tokens").asInt(0)
        );
    }

    protected static int getInputTokens(JsonNode usage) {
        return usage.path("input_tokens").asInt(
                usage.path("prompt_tokens").asInt(0));
    }
}
