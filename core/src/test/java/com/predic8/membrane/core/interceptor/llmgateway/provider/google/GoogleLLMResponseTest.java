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

package com.predic8.membrane.core.interceptor.llmgateway.provider.google;

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

class GoogleLLMResponseTest {

    private static final String URL = "http://localhost/v1beta/models/gemini-2.5-pro:streamGenerateContent";

    private final List<LLMResponse> processed = new ArrayList<>();

    /**
     * The Gemini stream has no terminal event, so the usage is only complete once the body ends.
     */
    @Test
    void usageOfStreamedResponseIsReportedOnceAtEndOfBody() throws URISyntaxException {
        stream("""
                data: {"candidates":[{"content":{"parts":[{"text":"Hi"}]}}],"usageMetadata":{"promptTokenCount":11}}

                data: {"candidates":[{"content":{"parts":[{"text":"!"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":7,"totalTokenCount":18}}

                """);

        assertEquals(1, processed.size());
        assertEquals(new Usage(11, 7, 18), processed.getFirst().getUsage());
    }

    @Test
    void thoughtTokensOfStreamedResponseCountAsOutput() throws URISyntaxException {
        stream("""
                data: {"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":7,"thoughtsTokenCount":5,"totalTokenCount":22}}

                """);

        assertEquals(1, processed.size());
        assertEquals(new Usage(10, 12, 22), processed.getFirst().getUsage());
    }

    @Test
    void usageOfNonStreamedResponseIsReportedOnce() throws URISyntaxException {
        var exchange = post(URL).json("{}").buildExchange();
        exchange.setResponse(Response.ok().json("""
                {"usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":6,"totalTokenCount":11}}""").build());

        new GoogleLLMResponse(exchange, processed::add);

        assertEquals(1, processed.size());
        assertEquals(new Usage(5, 6, 11), processed.getFirst().getUsage());
    }

    private void stream(String sse) throws URISyntaxException {
        var exchange = post(URL).json("{}").buildExchange();
        exchange.setResponse(Response.ok()
                .header(CONTENT_TYPE, "text/event-stream")
                .body(new ByteArrayInputStream(sse.getBytes(UTF_8)), false)
                .build());

        new GoogleLLMResponse(exchange, processed::add);

        exchange.getResponse().getBody().read();
    }
}
