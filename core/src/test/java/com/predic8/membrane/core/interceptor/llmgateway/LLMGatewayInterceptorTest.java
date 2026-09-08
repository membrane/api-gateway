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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsProvider;
import com.predic8.membrane.core.interceptor.llmgateway.store.AiApiUser;
import com.predic8.membrane.core.openapi.validators.MultipartBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.llmgateway.store.AiApiStoreFixtures.initializedStoreWith;
import static com.predic8.membrane.core.interceptor.llmgateway.store.AiApiStoreFixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LLMGatewayInterceptorTest {

    private static final String CHAT_COMPLETIONS = "http://localhost/v1/chat/completions";

    private static final String CHAT_REQUEST = """
            {"model":"gpt-4o","messages":[{"role":"user","content":"Hi"}]}""";

    private LLMGatewayInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LLMGatewayInterceptor();
        interceptor.setProvider(new ChatCompletionsProvider());
        interceptor.init();
    }

    @Test
    void postWithNonJsonBodyIsRejected() throws Exception {
        var exchange = post(CHAT_COMPLETIONS)
                .header(CONTENT_TYPE, "text/plain")
                .body("hello")
                .buildExchange();

        assertEquals(ABORT, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void postWithJsonBodyIsForwarded() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(chatCompletionExchange(null)));
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

    /**
     * Zero means unlimited, on the client's side as well as the policies', so it must not win the
     * comparison and leave the output tokens unreserved.
     */
    @ParameterizedTest(name = "client={0}, policy={1} -> {2}")
    @CsvSource({
            "4000, 200, 200",   // the policy caps what the client asked for
            " 100, 200, 100",   // the client asks for less than the policy allows
            "4000,   0, 4000",  // without a policy limit the client's request is reserved
            "  -1, 200, 200",   // the client asked for nothing, so the policy limit is reserved
            "   0, 200, 200",
            "  -1,   0, 0",     // neither side gives a limit
            "   0,   0, 0"
    })
    void effectiveMaxOutputTokens(long requested, long policyLimit, long expected) {
        assertEquals(expected, interceptor.computeEffectiveMaxOutputTokens(requested, policyLimit));
    }

    private static Exchange chatCompletionExchange(String apiKey) throws URISyntaxException {
        var request = post(CHAT_COMPLETIONS).json(CHAT_REQUEST);
        if (apiKey != null)
            request.header(AUTHORIZATION, "Bearer " + apiKey);
        return request.buildExchange();
    }

    /**
     * A rejected request is an aborted one: the response flow has nothing left to do with it.
     */
    @Nested
    class WithUserStore {

        @BeforeEach
        void useUserStore() {
            interceptor.setAiStore(initializedStoreWith(user("Alice", "key-a", 0)));
            // The outer setUp() initialized the interceptor with the store it had then.
            interceptor.init();
        }

        @Test
        void unknownApiKeyIsRejected() throws Exception {
            var exchange = post(CHAT_COMPLETIONS)
                    .header(AUTHORIZATION, "Bearer key-x")
                    .json("{}")
                    .buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(401, exchange.getResponse().getStatusCode());
        }

        @Test
        void knownApiKeyIsForwarded() throws Exception {
            assertEquals(CONTINUE, interceptor.handleRequest(chatCompletionExchange("key-a")));
        }
    }

    /**
     * A store that only records usage does not authenticate, so it must not turn every request into
     * a 401.
     */
    @Nested
    class WithRecordingOnlyStore {

        private final List<AiApiUser> recorded = new ArrayList<>();

        @BeforeEach
        void useRecordingStore() {
            interceptor.setAiStore((user, usage) -> recorded.add(user));
            // The outer setUp() initialized the interceptor with the store it had then.
            interceptor.init();
        }

        @Test
        void requestIsNotRejected() throws Exception {
            assertEquals(CONTINUE, interceptor.handleRequest(chatCompletionExchange(null)));
        }

        @Test
        void usageIsRecordedWithoutAUser() throws Exception {
            var exchange = post(CHAT_COMPLETIONS).json("{}").buildExchange();
            exchange.setResponse(Response.ok().json("""
                    {"usage":{"prompt_tokens":5,"completion_tokens":6,"total_tokens":11}}""").build());

            assertEquals(CONTINUE, interceptor.handleResponse(exchange));

            assertEquals(1, recorded.size());
            assertNull(recorded.getFirst());
        }
    }
}
