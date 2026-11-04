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
import com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeProvider;
import com.predic8.membrane.core.interceptor.llmgateway.provider.google.GoogleProvider;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.OpenAIProvider;
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
import static com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeLLMRequest.X_API_KEY;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.google.GoogleLLMRequest.X_GOOG_API_KEY;
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
     * An endpoint the provider has no model input adapter for bypasses the model rules and the token
     * budget. Once either is configured, such a request must be rejected before it gets the shared key.
     */
    @Nested
    class UnrecognizedEndpoint {

        private static final String EMBEDDINGS = "http://localhost/v1/embeddings";

        private static final String EMBEDDINGS_REQUEST = """
                {"model":"text-embedding-3-large","input":"Hi"}""";

        @BeforeEach
        void useOpenAIWithSharedKey() {
            interceptor.setProvider(new OpenAIProvider());
            interceptor.setApiKey("shared-key");
        }

        @Test
        void isRejectedWithModelRules() throws Exception {
            var policies = new DefaultPolicies();
            policies.setModels(List.of("gpt-4o-mini"));
            interceptor.setPolicies(policies);
            interceptor.init();

            var exchange = post(EMBEDDINGS).header(AUTHORIZATION, "Bearer client-key").json(EMBEDDINGS_REQUEST).buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(400, exchange.getResponse().getStatusCode());
            assertEquals("Bearer client-key", exchange.getRequest().getHeader().getFirstValue(AUTHORIZATION));
        }

        @Test
        void isRejectedWithUserStore() throws Exception {
            interceptor.setAiStore(initializedStoreWith(user("Alice", "key-a", 0)));
            interceptor.init();

            var exchange = post(EMBEDDINGS).header(AUTHORIZATION, "Bearer key-a").json(EMBEDDINGS_REQUEST).buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(400, exchange.getResponse().getStatusCode());
            assertEquals("Bearer key-a", exchange.getRequest().getHeader().getFirstValue(AUTHORIZATION));
        }

        /**
         * The configured system prompt cannot be added to an endpoint the gateway does not read, so the
         * request must not get through without it.
         */
        @Test
        void isRejectedWithSystemPrompt() throws Exception {
            var systemPrompt = new SystemPrompt();
            systemPrompt.setContent("configured");
            interceptor.setSystemPrompt(systemPrompt);
            interceptor.init();

            var exchange = post(EMBEDDINGS).header(AUTHORIZATION, "Bearer client-key").json(EMBEDDINGS_REQUEST).buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(400, exchange.getResponse().getStatusCode());
            assertEquals("Bearer client-key", exchange.getRequest().getHeader().getFirstValue(AUTHORIZATION));
        }

        /**
         * A request without a body carries no model input the gateway could check.
         */
        @Test
        void getIsRejectedWithUserStore() throws Exception {
            interceptor.setAiStore(initializedStoreWith(user("Alice", "key-a", 0)));
            interceptor.init();

            var exchange = get("http://localhost/v1/files").header(AUTHORIZATION, "Bearer key-a").buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(400, exchange.getResponse().getStatusCode());
        }

        @Test
        void isForwardedWithSharedKeyWithoutRestrictions() throws Exception {
            interceptor.init();

            var exchange = post(EMBEDDINGS).header(AUTHORIZATION, "Bearer client-key").json(EMBEDDINGS_REQUEST).buildExchange();

            assertEquals(CONTINUE, interceptor.handleRequest(exchange));
            assertEquals("Bearer shared-key", exchange.getRequest().getHeader().getFirstValue(AUTHORIZATION));
        }
    }

    /**
     * Every provider only reads model input from the endpoints it knows. Another path, like a batch
     * endpoint, carries model input the restrictions cannot be applied to, so it must not get the
     * shared key once restrictions are configured.
     */
    @Nested
    class ProviderEndpoints {

        @BeforeEach
        void useSharedKeyAndUserStore() {
            interceptor.setApiKey("shared-key");
            interceptor.setAiStore(initializedStoreWith(user("Alice", "key-a", 0)));
        }

        @ParameterizedTest(name = "{0} POST {1}")
        @CsvSource({
                "claude, /v1/messages/batches",
                "claude, /v1/messages/count_tokens",
                "claude, /v1/messagesX",
                "google, /v1beta/models/gemini-2.5-flash:batchGenerateContent",
                "google, /v1beta/models/gemini-2.5-flash:countTokens",
                "google, /v1beta/models/gemini-2.5-flash:embedContent",
                "google, /v1beta/cachedContents",
                "chatCompletions, /v1/embeddings",
                "chatCompletions, /v1/audio/transcriptions",
                "chatCompletions, /v1/chat/completions/abc",
                "openai, /v1/responses/resp_1/cancel",
                "openai, /v1/responses/input_tokens",
                "openai, /v1/chat/completions/abc",
                "openai, /v1/chat/completionsX",
        })
        void unsupportedEndpointIsRejected(String provider, String path) throws Exception {
            useProvider(provider);

            var exchange = post("http://localhost" + path).header(keyHeader(provider), keyValue(provider, "key-a")).json(CHAT_REQUEST).buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(400, exchange.getResponse().getStatusCode());
            assertEquals(keyValue(provider, "key-a"), exchange.getRequest().getHeader().getFirstValue(keyHeader(provider)));
        }

        @ParameterizedTest(name = "{0} POST {1}")
        @CsvSource({
                "claude, /v1/messages",
                "claude, /v1/messages?beta=true",
                "google, /v1beta/models/gemini-2.5-flash:generateContent",
                "google, /v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
                "google, /v1beta/models/gemini-2.5-flash%3AgenerateContent",
                "google, /v1/models/gemini-2.5-flash:generateContent",
                "chatCompletions, /v1/chat/completions",
                "chatCompletions, /openai/v1/chat/completions",
                "openai, /v1/chat/completions",
                "openai, /v1/responses",
                "openai, /v1/responses?x=1",
        })
        void supportedEndpointGetsSharedKey(String provider, String path) throws Exception {
            useProvider(provider);

            var exchange = post("http://localhost" + path).header(keyHeader(provider), keyValue(provider, "key-a")).json(CHAT_REQUEST).buildExchange();

            assertEquals(CONTINUE, interceptor.handleRequest(exchange));
            assertEquals(keyValue(provider, "shared-key"), exchange.getRequest().getHeader().getFirstValue(keyHeader(provider)));
        }

        /**
         * Without restrictions every endpoint is forwarded, with the shared key in the header the
         * provider expects it in.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource({"claude", "google", "chatCompletions", "openai"})
        void unsupportedEndpointGetsSharedKeyWithoutRestrictions(String provider) throws Exception {
            interceptor.setAiStore(null);
            useProvider(provider);

            var exchange = post("http://localhost/v1/batches").header(keyHeader(provider), keyValue(provider, "client-key")).json("{}").buildExchange();

            assertEquals(CONTINUE, interceptor.handleRequest(exchange));
            assertEquals(keyValue(provider, "shared-key"), exchange.getRequest().getHeader().getFirstValue(keyHeader(provider)));
        }

        /**
         * A batch request carries no model input the gateway can check, so it is rejected.
         */
        @Test
        void claudeBatchIsRejectedAsUnsupportedEndpoint() throws Exception {
            interceptor.setAiStore(null);
            var policies = new DefaultPolicies();
            policies.setMaxInputTokens(10);
            interceptor.setPolicies(policies);
            useProvider("claude");

            var batch = """
                    {"requests":[{"custom_id":"1","params":{"model":"claude-sonnet-4-5","max_tokens":10,
                    "messages":[{"role":"user","content":"%s"}]}}]}""".formatted("x".repeat(4000));
            var exchange = post("http://localhost/v1/messages/batches").header(X_API_KEY, "client-key").json(batch).buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(400, exchange.getResponse().getStatusCode());
            assertEquals("client-key", exchange.getRequest().getHeader().getFirstValue(X_API_KEY));
        }

        @Test
        void claudeMessageOverInputTokenLimitIsRejected() throws Exception {
            interceptor.setAiStore(null);
            var policies = new DefaultPolicies();
            policies.setMaxInputTokens(10);
            interceptor.setPolicies(policies);
            useProvider("claude");

            var message = """
                    {"model":"claude-sonnet-4-5","max_tokens":10,"messages":[{"role":"user","content":"%s"}]}"""
                    .formatted("x".repeat(4000));
            var exchange = post("http://localhost/v1/messages").header(X_API_KEY, "client-key").json(message).buildExchange();

            assertEquals(ABORT, interceptor.handleRequest(exchange));
            assertEquals(400, exchange.getResponse().getStatusCode());
        }

        private void useProvider(String provider) {
            interceptor.setProvider(switch (provider) {
                case "claude" -> new ClaudeProvider();
                case "google" -> new GoogleProvider();
                case "chatCompletions" -> new ChatCompletionsProvider();
                case "openai" -> new OpenAIProvider();
                default -> throw new IllegalArgumentException(provider);
            });
            interceptor.init();
        }

        private static String keyHeader(String provider) {
            return switch (provider) {
                case "claude" -> X_API_KEY;
                case "google" -> X_GOOG_API_KEY;
                default -> AUTHORIZATION;
            };
        }

        private static String keyValue(String provider, String key) {
            return AUTHORIZATION.equals(keyHeader(provider)) ? "Bearer " + key : key;
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
