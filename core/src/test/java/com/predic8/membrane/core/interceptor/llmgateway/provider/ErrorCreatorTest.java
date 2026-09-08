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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.google.GoogleErrorCreator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.http.Header.WWW_AUTHENTICATE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the wire shape of the error bodies each provider produces. Clients parse these, so the
 * envelope, the codes and the wording are part of the contract.
 */
class ErrorCreatorTest {

    private static final ObjectMapper om = new ObjectMapper();

    private static JsonNode body(Response response) throws Exception {
        return om.readTree(response.getBodyAsStringDecoded());
    }

    @Nested
    class ChatCompletions {

        private final ChatCompletionsErrorCreator creator = new ChatCompletionsErrorCreator();

        @Test
        void invalidRequest() throws Exception {
            var response = creator.invalidRequestError("Broken.");

            assertEquals(400, response.getStatusCode());
            var error = body(response).path("error");
            assertEquals("Broken.", error.path("message").asText());
            assertEquals("invalid_request_error", error.path("type").asText());
            assertEquals("bad_request", error.path("code").asText());
            assertTrue(error.path("param").isNull());
        }

        @Test
        void tokenLimitExceeded() throws Exception {
            var response = creator.tokenLimitExceeded(100, 10, 30);

            assertEquals(429, response.getStatusCode());
            var error = body(response).path("error");
            assertEquals("Token rate limit exceeded. Request requires 100 tokens but only 10 remain. Please wait 30 seconds before retrying.",
                    error.path("message").asText());
            assertEquals("rate_limit_error", error.path("type").asText());
            assertEquals("token_limit_exceeded", error.path("code").asText());
        }

        @Test
        void modelNotAllowed() throws Exception {
            var response = creator.modelNotAllowed("gpt-9", List.of("a", "b"));

            assertEquals(400, response.getStatusCode());
            var error = body(response).path("error");
            assertEquals("Model 'gpt-9' is not allowed. Allowed models: a, b.", error.path("message").asText());
            assertEquals("model_not_allowed", error.path("code").asText());
        }

        @Test
        void authenticationFailed() throws Exception {
            var response = creator.authenticationFailed();

            assertEquals(401, response.getStatusCode());
            assertEquals("Bearer", response.getHeader().getFirstValue(WWW_AUTHENTICATE));
            var error = body(response).path("error");
            assertEquals("Invalid authentication credentials", error.path("message").asText());
            assertEquals("invalid_authentication", error.path("code").asText());
        }

        @Test
        void inputTokensExceeded() throws Exception {
            var response = creator.inputTokensExceeded(50, 120);

            assertEquals(400, response.getStatusCode());
            var error = body(response).path("error");
            assertEquals("This model's maximum context length is 50 tokens.\nYour request contains approximately 120 tokens.",
                    error.path("message").asText());
            assertEquals("input", error.path("param").asText());
            assertEquals("context_length_exceeded", error.path("code").asText());
        }
    }

    @Nested
    class Claude {

        private final ClaudeErrorCreator creator = new ClaudeErrorCreator();

        @Test
        void invalidRequest() throws Exception {
            var response = creator.invalidRequestError("Broken.");

            assertEquals(400, response.getStatusCode());
            var json = body(response);
            assertEquals("error", json.path("type").asText());
            assertTrue(json.path("request_id").asText().startsWith("membrane_"));
            assertEquals("invalid_request_error", json.path("error").path("type").asText());
            assertEquals("Broken.", json.path("error").path("message").asText());
        }

        /**
         * A negative remainder is reported as zero, the client has no use for the overdraft.
         */
        @Test
        void tokenLimitExceededHidesANegativeRemainder() throws Exception {
            var response = creator.tokenLimitExceeded(100, -5, 30);

            assertEquals(429, response.getStatusCode());
            var error = body(response).path("error");
            assertEquals("rate_limit_error", error.path("type").asText());
            assertEquals("Token rate limit exceeded.\nRequest requires 100 tokens but only 0 remain.\nRetry after 30 seconds.",
                    error.path("message").asText());
        }

        @Test
        void modelNotAllowed() throws Exception {
            var error = body(creator.modelNotAllowed("claude-9", List.of("a", "b"))).path("error");

            assertEquals("invalid_request_error", error.path("type").asText());
            assertEquals("Model 'claude-9' is not allowed. Allowed models: a, b.", error.path("message").asText());
        }

        @Test
        void authenticationFailed() throws Exception {
            var response = creator.authenticationFailed();

            assertEquals(401, response.getStatusCode());
            assertEquals("Bearer", response.getHeader().getFirstValue(WWW_AUTHENTICATE));
            var error = body(response).path("error");
            assertEquals("authentication_error", error.path("type").asText());
            assertEquals("Invalid bearer token", error.path("message").asText());
        }

        @Test
        void inputTokensExceeded() throws Exception {
            var response = creator.inputTokensExceeded(50, 120);

            assertEquals(400, response.getStatusCode());
            assertEquals("prompt is too long:\n120 tokens > 50 maximum",
                    body(response).path("error").path("message").asText());
        }
    }

    @Nested
    class Google {

        private final GoogleErrorCreator creator = new GoogleErrorCreator();

        @Test
        void invalidRequest() throws Exception {
            var response = creator.invalidRequestError("Broken.");

            assertEquals(400, response.getStatusCode());
            var error = body(response).path("error");
            assertEquals(400, error.path("code").asInt());
            assertEquals("Broken.", error.path("message").asText());
            assertEquals("INVALID_ARGUMENT", error.path("status").asText());
        }

        @Test
        void tokenLimitExceededHidesANegativeRemainder() throws Exception {
            var response = creator.tokenLimitExceeded(100, -5, 30);

            assertEquals(429, response.getStatusCode());
            var error = body(response).path("error");
            assertEquals(429, error.path("code").asInt());
            assertEquals("RESOURCE_EXHAUSTED", error.path("status").asText());
            assertEquals("Token rate limit exceeded.\nRequest requires 100 tokens but only 0 remain.\nRetry after 30 seconds.",
                    error.path("message").asText());
        }

        @Test
        void modelNotAllowed() throws Exception {
            var error = body(creator.modelNotAllowed("gemini-9", List.of("a", "b"))).path("error");

            assertEquals("INVALID_ARGUMENT", error.path("status").asText());
            assertEquals("Model 'gemini-9' is not allowed. Allowed models: a, b.", error.path("message").asText());
        }

        @Test
        void authenticationFailed() throws Exception {
            var response = creator.authenticationFailed();

            assertEquals(401, response.getStatusCode());
            assertEquals("Bearer", response.getHeader().getFirstValue(WWW_AUTHENTICATE));
            var error = body(response).path("error");
            assertEquals(401, error.path("code").asInt());
            assertEquals("UNAUTHENTICATED", error.path("status").asText());
            assertEquals("Invalid API key.", error.path("message").asText());
        }

        @Test
        void inputTokensExceeded() throws Exception {
            var error = body(creator.inputTokensExceeded(50, 120)).path("error");

            assertEquals("The input token count (120) exceeds the maximum allowed (50).",
                    error.path("message").asText());
        }
    }
}
