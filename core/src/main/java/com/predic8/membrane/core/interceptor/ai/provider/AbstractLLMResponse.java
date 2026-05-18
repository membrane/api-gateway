package com.predic8.membrane.core.interceptor.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractMessageObserver;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.util.http.SSEParser;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public abstract class AbstractLLMResponse extends AbstractLLMMessage implements LLMResponse {

    private static final Logger log = LoggerFactory.getLogger(AbstractLLMResponse.class);

    protected ObjectNode json;
    protected Consumer<LLMResponse> postProcessor;

    public AbstractLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange);
        this.postProcessor = postProcessor;
        var msg = exchange.getResponse();

        if (msg.isStream()) {

            log.debug("Streaming response.");

            var parser = new SSEParser(getTerminalEvents());

            msg.getBody().addObserver(new AbstractMessageObserver() {
                @Override
                public void bodyChunk(Chunk chunk) {
                    processChunk(chunk, parser);
                }
            });
        } else {
            json = JsonUtil.getJsonObject(exchange.getResponse())
                    .orElse(JsonNodeFactory.instance.objectNode().put("error", "No JSON object response from model."));
            postProcessor.accept(this);
        }
    }

    protected void processChunk(Chunk chunk, SSEParser parser) {
        // Wait for terminal chunk
        if (!parser.parse(chunk)) {
            return;
        }

        // Now all chunks are parsed

        var events = parser.getEvents();
        var terminal = parser.getTerminalEvent();

        log.debug("Events: {}", events.size());
        events.forEach(this::process);

        terminal.ifPresent(event -> {
            // Terminal of old chat completion API
            if ("[DONE]".equals(event.data()))
                return;
            json = JsonUtil.getJsonObject(event.data())
                    .orElse(JsonNodeFactory.instance.objectNode()
                            .put("error", "No JSON object response from model."));

            // All is read, call postProcessor
            postProcessor.accept(AbstractLLMResponse.this);
        });
    }

    @Override
    public boolean isError() {
        return json.get("error") != null && !json.get("error").isNull();
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
