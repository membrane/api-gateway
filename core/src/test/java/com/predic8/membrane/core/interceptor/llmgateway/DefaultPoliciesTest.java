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
import com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeLLMRequest;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

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

    private static ModelInputRequest request(String body) throws Exception {
        return new ClaudeLLMRequest(post("http://localhost/v1/messages").json(body).buildExchange());
    }
}
