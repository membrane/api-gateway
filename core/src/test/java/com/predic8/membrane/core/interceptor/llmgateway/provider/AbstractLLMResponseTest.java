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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ChunkedBody;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every provider's response has to do with a body, streamed or not: report the usage to the
 * post processor exactly once. Subclasses supply the endpoint and the response under test.
 */
public abstract class AbstractLLMResponseTest {

    protected final List<LLMResponse> processed = new ArrayList<>();

    /**
     * The endpoint the provider is called at, so that the request looks like the real one.
     */
    protected abstract String url();

    /**
     * The response under test, reporting to {@link #processed}. Not started yet.
     */
    protected abstract AbstractLLMResponse createResponse(Exchange exchange);

    /**
     * Registers the response under test on the exchange, the way its provider does.
     */
    protected LLMResponse newResponse(Exchange exchange) {
        var response = createResponse(exchange);
        response.start();
        return response;
    }

    /**
     * Registers the response on a streamed body and then reads it, the way the exchange would.
     */
    protected void stream(String sse) throws URISyntaxException {
        var exchange = post(url()).json("{}").buildExchange();
        exchange.setResponse(Response.ok()
                .header(CONTENT_TYPE, "text/event-stream")
                .body(new ByteArrayInputStream(sse.getBytes(UTF_8)), false)
                .build());

        newResponse(exchange);

        exchange.getResponse().getBody().read();
    }

    /**
     * An exchange whose response body delivers the given SSE text one chunk at a time.
     */
    protected Exchange chunked(String... sseChunks) throws URISyntaxException {
        var builder = chunks();
        for (var chunk : sseChunks)
            builder.add(chunk);

        var response = Response.ok().header(CONTENT_TYPE, "text/event-stream").build();
        response.setBody(new ChunkedBody(new ByteArrayInputStream(builder.build())));

        var exchange = post(url()).json("{}").buildExchange();
        exchange.setResponse(response);
        return exchange;
    }

    /**
     * An exchange answered with a complete JSON body, the way a non-streamed call returns.
     */
    protected Exchange withJsonResponse(String json) throws URISyntaxException {
        var exchange = post(url()).json("{}").buildExchange();
        exchange.setResponse(Response.ok().json(json).build());
        return exchange;
    }

    /**
     * Construction alone does nothing: the response only reaches the body once it is started, so it
     * cannot be handed to another thread before its subclass has finished initialising.
     */
    @Test
    void aResponseThatWasNotStartedReportsNothing() throws URISyntaxException {
        createResponse(withJsonResponse("""
                {"usage":{"input_tokens":1,"output_tokens":1}}"""));

        assertTrue(processed.isEmpty());
    }

    protected void assertUsage(Usage expected) {
        assertEquals(1, processed.size());
        assertEquals(expected, processed.getFirst().getUsage());
    }
}
