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
import com.predic8.membrane.core.interceptor.llmgateway.provider.ModelInputRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeLLMRequest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.openai.OpenAIChatCompletionsRequest;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.*;

class DefaultPoliciesTest {

    @Test
    void aRequestWithoutPoliciesPasses() throws Exception {
        var exchange = new Exchange(null);

        assertEquals(CONTINUE, policies().handleRequest(request("{}"), exchange));
        assertNull(exchange.getResponse());
    }

    @Test
    void anAllowedModelPasses() throws Exception {
        var policies = policies();
        policies.setModels(List.of("claude-sonnet-5"));

        assertEquals(CONTINUE, policies.handleRequest(request("""
                {"model":"claude-sonnet-5"}"""), new Exchange(null)));
    }

    @Test
    void aModelOutsideTheListIsRejected() throws Exception {
        var policies = policies();
        policies.setModels(List.of("claude-sonnet-5"));
        var exchange = new Exchange(null);

        assertEquals(ABORT, policies.handleRequest(request("""
                {"model":"other"}"""), exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    /**
     * Without a requested limit the configured maximum is written into the request.
     */
    @Test
    void maxOutputTokensIsAppliedWhenTheClientAsksForNoLimit() throws Exception {
        var policies = policies();
        policies.setMaxOutputTokens(100);
        var request = request("{}");

        assertEquals(CONTINUE, policies.handleRequest(request, new Exchange(null)));

        assertEquals(100, request.getRequestedMaxOutputTokens());
    }

    @Test
    void aRequestedLimitAboveTheMaximumIsLowered() throws Exception {
        var policies = policies();
        policies.setMaxOutputTokens(100);
        var request = request("""
                {"max_tokens":500}""");

        assertEquals(CONTINUE, policies.handleRequest(request, new Exchange(null)));

        assertEquals(100, request.getRequestedMaxOutputTokens());
    }

    @Test
    void aRequestedLimitBelowTheMaximumIsKept() throws Exception {
        var policies = policies();
        policies.setMaxOutputTokens(100);
        var request = request("""
                {"max_tokens":50}""");

        assertEquals(CONTINUE, policies.handleRequest(request, new Exchange(null)));

        assertEquals(50, request.getRequestedMaxOutputTokens());
    }

    /**
     * Chat Completions has two output limit fields, max_tokens and max_completion_tokens. Neither may exceed the
     * maximum, whichever one the provider writes the limit to.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "chatCompletions | {\"max_completion_tokens\":100000}",
            "chatCompletions | {\"max_completion_tokens\":50,\"max_tokens\":100000}",
            "chatCompletions | {\"max_tokens\":50,\"max_completion_tokens\":100000}",
            "openai          | {\"max_tokens\":100000}",
            "openai          | {\"max_completion_tokens\":50,\"max_tokens\":100000}",
            "openai          | {\"max_tokens\":50,\"max_completion_tokens\":100000}",
    })
    void noChatCompletionsOutputLimitFieldExceedsTheMaximum(String provider, String body) throws Exception {
        final var policies = policies();
        policies.setMaxOutputTokens(100);
        final var request = chatCompletionsRequest(provider, body);

        assertEquals(CONTINUE, policies.handleRequest(request, new Exchange(null)));

        for (final var field : List.of("max_tokens", "max_completion_tokens")) {
            final var value = request.getJson().path(field);
            assertTrue(value.isMissingNode() || value.asLong() <= 100, () -> "%s is %s".formatted(field, value));
        }
    }

    /**
     * A field below the maximum is kept as the client sent it.
     */
    @Test
    void aChatCompletionsLimitBelowTheMaximumIsKept() throws Exception {
        final var policies = policies();
        policies.setMaxOutputTokens(100);
        final var request = chatCompletionsRequest("chatCompletions", """
                {"max_tokens":50,"max_completion_tokens":100000}""");

        assertEquals(CONTINUE, policies.handleRequest(request, new Exchange(null)));

        assertEquals(50, request.getJson().path("max_tokens").asLong());
        assertEquals(100, request.getJson().path("max_completion_tokens").asLong());
    }

    @ParameterizedTest
    @CsvSource({"chatCompletions, max_tokens", "openai, max_completion_tokens"})
    void withoutAClientLimitTheProvidersFieldIsSet(String provider, String field) throws Exception {
        final var policies = policies();
        policies.setMaxOutputTokens(100);
        final var request = chatCompletionsRequest(provider, "{}");

        assertEquals(CONTINUE, policies.handleRequest(request, new Exchange(null)));

        assertEquals(1, request.getJson().size());
        assertEquals(100, request.getJson().path(field).asLong());
    }

    @Test
    void tooManyInputTokensAreRejected() throws Exception {
        var policies = policies();
        policies.setMaxInputTokens(1);
        var exchange = new Exchange(null);

        // 40 characters of system prompt are estimated as 10 tokens
        assertEquals(ABORT, policies.handleRequest(request("""
                {"system":"0123456789012345678901234567890123456789"}"""), exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void inputTokensWithinTheLimitPass() throws Exception {
        var policies = policies();
        policies.setMaxInputTokens(100);

        assertEquals(CONTINUE, policies.handleRequest(request("""
                {"system":"0123456789"}"""), new Exchange(null)));
    }

    @Test
    void negativeLimitsAreRejectedAtConfigurationTime() {
        assertThrows(ConfigurationException.class, () -> policies().setMaxOutputTokens(-1));
        assertThrows(ConfigurationException.class, () -> policies().setMaxInputTokens(-1));
    }

    private static DefaultPolicies policies() {
        var policies = new DefaultPolicies();
        policies.init(new ChatCompletionsErrorCreator());
        return policies;
    }

    private static ModelInputRequest chatCompletionsRequest(String provider, String body) throws Exception {
        final var exchange = post("http://localhost/v1/chat/completions").json(body).buildExchange();
        return "openai".equals(provider) ? new OpenAIChatCompletionsRequest(exchange) : new ChatCompletionsRequest(exchange);
    }

    private static ModelInputRequest request(String body) throws Exception {
        return new ClaudeLLMRequest(post("http://localhost/v1/messages").json(body).buildExchange());
    }
}
