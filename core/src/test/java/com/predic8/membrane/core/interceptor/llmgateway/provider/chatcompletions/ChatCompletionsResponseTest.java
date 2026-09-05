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
import com.predic8.membrane.core.http.ChunkedBody;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.ChunksBuilder.chunks;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.Request.post;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCompletionsResponseTest {

    private final List<LLMResponse> processed = new ArrayList<>();

    /**
     * The usage arrives in a chunk of its own with an empty choices array, requested by
     * ChatCompletionsRequest via stream_options.include_usage. The terminal [DONE] carries no JSON.
     */
    @Test
    void usageOfStreamedResponseIsReportedExactlyOnce() throws URISyntaxException {
        stream("""
                data: {"object":"chat.completion.chunk","choices":[{"delta":{"content":"Hi"}}]}

                data: {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}

                data: [DONE]

                """);

        assertEquals(1, processed.size());
        assertEquals(new Usage(11, 7, 18), processed.getFirst().getUsage());
    }

    @Test
    void streamWithoutUsageChunkReportsNoTokens() throws URISyntaxException {
        stream("""
                data: {"object":"chat.completion.chunk","choices":[{"delta":{"content":"Hi"}}]}

                data: [DONE]

                """);

        assertEquals(1, processed.size());
        assertEquals(new Usage(0, 0, 0), processed.getFirst().getUsage());
    }

    @Test
    void unparseableEventDoesNotBreakTheStream() throws URISyntaxException {
        stream("""
                data: not json at all

                data: {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}

                data: [DONE]

                """);

        assertEquals(1, processed.size());
        assertEquals(new Usage(3, 4, 7), processed.getFirst().getUsage());
    }

    /**
     * A stream cut off before [DONE] is still reported once, when the body ends.
     */
    @Test
    void truncatedStreamIsReportedOnce() throws URISyntaxException {
        stream("""
                data: {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}

                """);

        assertEquals(1, processed.size());
        assertEquals(new Usage(3, 4, 7), processed.getFirst().getUsage());
    }

    @Test
    void usageOfNonStreamedResponseIsReportedOnce() throws URISyntaxException {
        var exchange = post("http://localhost/v1/chat/completions").json("{}").buildExchange();
        exchange.setResponse(Response.ok().json("""
                {"usage":{"prompt_tokens":5,"completion_tokens":6,"total_tokens":11}}""").build());

        new ChatCompletionsResponse(exchange, processed::add);

        assertEquals(1, processed.size());
        assertEquals(new Usage(5, 6, 11), processed.getFirst().getUsage());
    }

    /**
     * The events of a stream are processed as their chunks arrive, not collected and handled at the
     * end, so arriving in several chunks must make no difference to what is reported.
     */
    @Test
    void streamSplitAcrossChunksIsReportedOnce() throws URISyntaxException {
        var exchange = chunked(
                "data: {\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n",
                "data: {\"object\":\"chat.completion.chunk\",\"choices\":[],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7,\"total_tokens\":18}}\n\n",
                "data: [DONE]\n\n");

        new ChatCompletionsResponse(exchange, processed::add);

        exchange.getResponse().getBody().read();

        assertEquals(1, processed.size());
        assertEquals(new Usage(11, 7, 18), processed.getFirst().getUsage());
    }

    /**
     * The events of a chunk are handed to the subclass while the body is still arriving, so that a
     * long stream is not held in memory as a whole. The count is read from an observer registered
     * after the response, which therefore sees each chunk once the response has handled it.
     */
    @Test
    void eventsAreProcessedWhileTheStreamIsStillArriving() throws URISyntaxException {
        var exchange = chunked(
                "data: {\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n",
                "data: {\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\" there\"}}]}\n\n",
                "data: [DONE]\n\n");

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

    /**
     * An exchange whose response body delivers the given SSE text one chunk at a time.
     */
    private static Exchange chunked(String... sseChunks) throws URISyntaxException {
        var builder = chunks();
        for (var chunk : sseChunks)
            builder.add(chunk);

        var response = Response.ok().header(CONTENT_TYPE, "text/event-stream").build();
        response.setBody(new ChunkedBody(new ByteArrayInputStream(builder.build())));

        var exchange = post("http://localhost/v1/chat/completions").json("{}").buildExchange();
        exchange.setResponse(response);
        return exchange;
    }

    /**
     * Registers the response on a streamed body and then reads it, the way the exchange would.
     */
    private void stream(String sse) throws URISyntaxException {
        var exchange = post("http://localhost/v1/chat/completions").json("{}").buildExchange();
        exchange.setResponse(Response.ok()
                .header(CONTENT_TYPE, "text/event-stream")
                .body(new ByteArrayInputStream(sse.getBytes(UTF_8)), false)
                .build());

        new ChatCompletionsResponse(exchange, processed::add);

        exchange.getResponse().getBody().read();
    }
}
