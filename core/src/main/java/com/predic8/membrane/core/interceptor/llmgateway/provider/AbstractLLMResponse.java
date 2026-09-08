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
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.AbstractMessageObserver;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public abstract class AbstractLLMResponse extends AbstractLLMMessage implements LLMResponse {

    private static final Logger log = LoggerFactory.getLogger(AbstractLLMResponse.class);

    /**
     * Written while the body arrives and read once it is through. Both can happen on different
     * threads, so the state a response accumulates is published for every reader.
     */
    protected volatile ObjectNode json;

    protected final Consumer<LLMResponse> postProcessor;

    private volatile boolean sawEvents;
    private final AtomicBoolean finished = new AtomicBoolean();

    protected AbstractLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange);
        this.postProcessor = postProcessor;
    }

    /**
     * Starts reading the response. Kept out of the constructor: it hands this response to the body
     * as an observer, and a reference that escapes a constructor can reach another thread before the
     * subclass has finished initialising. Call it once, right after construction.
     */
    public final void start() {
        var response = exchange.getResponse();
        if (response.isStream()) {
            observeStream(response);
        } else {
            readCompleteBody(response);
        }
    }

    /**
     * The body arrives chunk by chunk, so the events are read as they come in and the response is
     * only reported once the stream has delivered what it has.
     */
    private void observeStream(Message response) {
        log.debug("Streaming response.");

        // Subclasses fill this in from the events they see, so it stays empty rather than null
        // until then.
        json = JsonNodeFactory.instance.objectNode();

        var parser = new SSEParser(getTerminalEvents());

        response.getBody().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyChunk(Chunk chunk) {
                processChunk(chunk, parser);
            }

            @Override
            public void bodyComplete(AbstractBody body) {
                // Not every stream has a terminal event: Gemini sends unnamed data events, so
                // the end of the body is the only end-of-stream signal there is.
                processPending(parser);
                finish(parser);
            }
        });
    }

    private void readCompleteBody(Message response) {
        json = JsonUtil.getJsonObject(response)
                .orElse(JsonNodeFactory.instance.objectNode().put("error", "No JSON object response from model."));
        postProcessor.accept(this);
    }

    protected void processChunk(Chunk chunk, SSEParser parser) {
        boolean terminalReached = parser.parse(chunk);

        processPending(parser);

        if (terminalReached)
            finish(parser);
    }

    /**
     * Hands the events of this chunk to the subclass and forgets them, so that a long stream is not
     * held in memory as a whole.
     */
    private void processPending(SSEParser parser) {
        var events = parser.drainEvents();
        if (events.isEmpty())
            return;

        log.debug("Events: {}", events.size());
        events.forEach(this::process);
        sawEvents = true;
    }

    /**
     * Reports the response to the post processor, at most once and only for a stream that actually
     * delivered something.
     */
    private void finish(SSEParser parser) {
        if (!sawEvents)
            return;

        // The terminal event and the end of the body both end a stream, and they can arrive on
        // different threads. Whoever gets here first reports, the other one drops out: reporting
        // twice would bill the usage twice.
        if (!finished.compareAndSet(false, true))
            return;

        parser.getTerminalEvent().ifPresent(this::processTerminalEvent);

        postProcessor.accept(this);
    }

    protected void processTerminalEvent(SSEParser.SSEEvent terminal) {}

    @Override
    public boolean isError() {
        return json.get("error") != null && !json.get("error").isNull();
    }

    /**
     * The usage as the OpenAI style providers report it, whichever of the two spellings they use.
     * The total is what the provider says, or the sum if it does not say.
     */
    protected static Usage usageFrom(JsonNode usage) {
        var inputTokens = getInputTokens(usage);
        var outputTokens = getOutputTokens(usage);
        return new Usage(inputTokens, outputTokens, usage.path("total_tokens").asInt(inputTokens + outputTokens));
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
