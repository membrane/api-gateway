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

package com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractMessageObserver;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMResponseTest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCompletionsResponseTest extends AbstractLLMResponseTest {

    private static final String HI_CHUNK = """
            data: {"object":"chat.completion.chunk","choices":[{"delta":{"content":"Hi"}}]}

            """;

    private static final String THERE_CHUNK = """
            data: {"object":"chat.completion.chunk","choices":[{"delta":{"content":" there"}}]}

            """;

    private static final String USAGE_CHUNK = """
            data: {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}

            """;

    private static final String DONE = """
            data: [DONE]

            """;

    @Override
    protected String url() {
        return "http://localhost/v1/chat/completions";
    }

    @Override
    protected LLMResponse newResponse(Exchange exchange) {
        return new ChatCompletionsResponse(exchange, processed::add);
    }

    /**
     * The usage arrives in a chunk of its own with an empty choices array, requested by
     * ChatCompletionsRequest via stream_options.include_usage. The terminal [DONE] carries no JSON.
     */
    @Test
    void usageOfStreamedResponseIsReportedExactlyOnce() throws URISyntaxException {
        stream(HI_CHUNK + USAGE_CHUNK + DONE);

        assertUsage(new Usage(11, 7, 18));
    }

    @Test
    void streamWithoutUsageChunkReportsNoTokens() throws URISyntaxException {
        stream(HI_CHUNK + DONE);

        assertUsage(new Usage(0, 0, 0));
    }

    @Test
    void unparseableEventDoesNotBreakTheStream() throws URISyntaxException {
        stream("""
                data: not json at all

                data: {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}

                """ + DONE);

        assertUsage(new Usage(3, 4, 7));
    }

    /**
     * A stream cut off before [DONE] is still reported once, when the body ends.
     */
    @Test
    void truncatedStreamIsReportedOnce() throws URISyntaxException {
        stream("""
                data: {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}

                """);

        assertUsage(new Usage(3, 4, 7));
    }

    @Test
    void usageOfNonStreamedResponseIsReportedOnce() throws URISyntaxException {
        newResponse(withJsonResponse("""
                {"usage":{"prompt_tokens":5,"completion_tokens":6,"total_tokens":11}}"""));

        assertUsage(new Usage(5, 6, 11));
    }

    /**
     * The events of a stream are processed as their chunks arrive, not collected and handled at the
     * end, so arriving in several chunks must make no difference to what is reported.
     */
    @Test
    void streamSplitAcrossChunksIsReportedOnce() throws URISyntaxException {
        var exchange = chunked(HI_CHUNK, USAGE_CHUNK, DONE);

        newResponse(exchange);

        exchange.getResponse().getBody().read();

        assertUsage(new Usage(11, 7, 18));
    }

    /**
     * The events of a chunk are handed to the subclass while the body is still arriving, so that a
     * long stream is not held in memory as a whole. The count is read from an observer registered
     * after the response, which therefore sees each chunk once the response has handled it.
     */
    @Test
    void eventsAreProcessedWhileTheStreamIsStillArriving() throws URISyntaxException {
        var exchange = chunked(HI_CHUNK, THERE_CHUNK, DONE);

        var llmResponse = new ChatCompletionsResponse(exchange, processed::add) {
            int eventCount;

            @Override
            public void process(SSEParser.SSEEvent event) {
                eventCount++;
                super.process(event);
            }
        };

        var eventsPerChunk = new ArrayList<Integer>();
        exchange.getResponse().getBody().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyChunk(Chunk chunk) {
                eventsPerChunk.add(llmResponse.eventCount);
            }
        });

        exchange.getResponse().getBody().read();

        assertEquals(List.of(1, 2, 3), eventsPerChunk);
    }
}
