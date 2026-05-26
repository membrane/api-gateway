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

package com.predic8.membrane.core.interceptor.llmgateway.provider;

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
            processTerminalEvent(event);
            postProcessor.accept(AbstractLLMResponse.this);
        });
    }

    protected void processTerminalEvent(SSEParser.SSEEvent terminal) {}

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
