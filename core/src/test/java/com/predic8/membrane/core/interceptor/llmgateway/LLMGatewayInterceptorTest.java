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

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsProvider;
import com.predic8.membrane.core.interceptor.llmgateway.store.AiApiUser;
import com.predic8.membrane.core.interceptor.llmgateway.store.SimpleAiApiStore;
import com.predic8.membrane.core.openapi.validators.MultipartBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        assertEquals(ABORT, interceptor.handleRequest(exchange));
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

    /**
     * Zero means unlimited, on the client's side as well as the policies', so it must not win the
     * comparison and leave the output tokens unreserved.
     */
    @Nested
    class EffectiveMaxOutputTokens {

        @Test
        void policyCapsWhatTheClientAskedFor() {
            assertEquals(200, interceptor.computeEffectiveMaxOutputTokens(4000, 200));
        }

        @Test
        void clientAsksForLessThanThePolicyAllows() {
            assertEquals(100, interceptor.computeEffectiveMaxOutputTokens(100, 200));
        }

        @Test
        void withoutAPolicyLimitTheClientsRequestIsReserved() {
            assertEquals(4000, interceptor.computeEffectiveMaxOutputTokens(4000, 0));
        }

        @Test
        void clientAskedForNothingSoThePolicyLimitIsReserved() {
            assertEquals(200, interceptor.computeEffectiveMaxOutputTokens(-1, 200));
            assertEquals(200, interceptor.computeEffectiveMaxOutputTokens(0, 200));
        }

        @Test
        void neitherSideGivesALimit() {
            assertEquals(0, interceptor.computeEffectiveMaxOutputTokens(-1, 0));
            assertEquals(0, interceptor.computeEffectiveMaxOutputTokens(0, 0));
        }
    }

    /**
     * A rejected request is an aborted one: the response flow has nothing left to do with it.
     */
    @Nested
    class WithUserStore {

        @BeforeEach
        void useUserStore() {
            var alice = new AiApiUser();
            alice.setName("Alice");
            alice.setApiKey("key-a");

            var store = new SimpleAiApiStore();
            store.setUsers(List.of(alice));
            interceptor.setAiStore(store);
            interceptor.init();
        }

        @Test
        void unknownApiKeyIsRejected() throws Exception {
            var exchange = post("http://localhost/v1/chat/completions")
                    .header(AUTHORIZATION, "Bearer key-x")
                    .json("{}")
                    .buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(401, exchange.getResponse().getStatusCode());
        }

        @Test
        void knownApiKeyIsForwarded() throws Exception {
            var exchange = post("http://localhost/v1/chat/completions")
                    .header(AUTHORIZATION, "Bearer key-a")
                    .json("""
                            {"model":"gpt-4o","messages":[{"role":"user","content":"Hi"}]}""")
                    .buildExchange();

            assertEquals(CONTINUE, interceptor.handleRequest(exchange));
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
            interceptor.init();
        }

        @Test
        void requestIsNotRejected() throws Exception {
            var exchange = post("http://localhost/v1/chat/completions")
                    .json("""
                            {"model":"gpt-4o","messages":[{"role":"user","content":"Hi"}]}""")
                    .buildExchange();

            assertEquals(CONTINUE, interceptor.handleRequest(exchange));
        }

        @Test
        void usageIsRecordedWithoutAUser() throws Exception {
            var exchange = post("http://localhost/v1/chat/completions").json("{}").buildExchange();
            exchange.setResponse(Response.ok().json("""
                    {"usage":{"prompt_tokens":5,"completion_tokens":6,"total_tokens":11}}""").build());

            assertEquals(CONTINUE, interceptor.handleResponse(exchange));

            assertEquals(1, recorded.size());
            assertNull(recorded.getFirst());
        }
    }
}
