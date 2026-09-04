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

package com.predic8.membrane.core.interceptor.llmgateway;

import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsProvider;
import com.predic8.membrane.core.openapi.validators.MultipartBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LLMGatewayInterceptorTest {

    private LLMGatewayInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LLMGatewayInterceptor();
        interceptor.setProvider(new ChatCompletionsProvider());
        interceptor.init();
    }

    @Test
    void postWithNonJsonBodyIsRejected() throws Exception {
        var exchange = post("http://localhost/v1/chat/completions")
                .header(CONTENT_TYPE, "text/plain")
                .body("hello")
                .buildExchange();

        assertEquals(RETURN, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void postWithJsonBodyIsForwarded() throws Exception {
        var exchange = post("http://localhost/v1/chat/completions")
                .json("""
                        {"model":"gpt-4o","messages":[{"role":"user","content":"Hi"}]}""")
                .buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exchange));
    }

    /**
     * The model input of a multipart request is in the parts, not in JSON.
     */
    @Test
    void multipartPostIsForwarded() throws Exception {
        var exchange = post("http://localhost/v1/audio/transcriptions")
                .header(CONTENT_TYPE, MultipartBuilder.CONTENT_TYPE)
                .body(new MultipartBuilder()
                        .part("model", null, "text/plain", null, "whisper-1")
                        .build())
                .buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exchange));
    }

    @Test
    void requestWithoutBodyIsForwarded() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(get("http://localhost/v1/models").buildExchange()));
    }
}
