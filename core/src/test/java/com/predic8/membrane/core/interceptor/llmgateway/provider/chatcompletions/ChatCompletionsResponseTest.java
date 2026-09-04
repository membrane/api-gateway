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

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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
