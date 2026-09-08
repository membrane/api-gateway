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
package com.predic8.membrane.core.interceptor.llmgateway.provider.claude;

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static com.predic8.membrane.core.http.Header.ACCEPT_ENCODING;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.llmgateway.provider.claude.ClaudeLLMRequest.X_API_KEY;
import static org.junit.jupiter.api.Assertions.*;

class ClaudeLLMRequestTest {

    @Test
    void asksForAnUncompressedResponse() throws Exception {
        var exchange = post("http://localhost/v1/messages").json("{}").buildExchange();

        new ClaudeLLMRequest(exchange);

        assertEquals("identity", exchange.getRequest().getHeader().getFirstValue(ACCEPT_ENCODING));
    }

    @Test
    void apiKeyIsReadFromAndWrittenToClaudeHeader() throws Exception {
        var exchange = post("http://localhost/v1/messages").json("{}").buildExchange();
        var request = new ClaudeLLMRequest(exchange);
        assertNull(request.getApiKey());

        request.setApiKey("secret");

        assertEquals("secret", request.getApiKey());
        assertEquals("secret", exchange.getRequest().getHeader().getFirstValue(X_API_KEY));
    }

    @Test
    void setMaxOutputTokensWritesMaxTokens() throws Exception {
        var request = request("{}");

        request.setMaxOutputTokens(500);

        assertEquals(500, request.getRequestedMaxOutputTokens());
    }

    /**
     * Thinking needs at least 2048 output tokens, so a lower limit turns it off.
     */
    @Test
    void aLowMaxOutputTokenLimitDisablesThinking() throws Exception {
        var request = request("""
                {"thinking":{"type":"enabled","budget_tokens":1024}}""");

        request.setMaxOutputTokens(1000);

        assertEquals("disabled", request.getJson().path("thinking").path("type").asText());
        assertEquals(1000, request.getRequestedMaxOutputTokens());
    }

    /**
     * budget_tokens has to stay below max_tokens.
     */
    @Test
    void aThinkingBudgetAboveTheLimitIsClamped() throws Exception {
        var request = request("""
                {"thinking":{"type":"enabled","budget_tokens":4000}}""");

        request.setMaxOutputTokens(3000);

        assertEquals("enabled", request.getJson().path("thinking").path("type").asText());
        assertEquals(1024, request.getJson().path("thinking").path("budget_tokens").asInt());
    }

    @Test
    void aThinkingBudgetBelowTheLimitIsKept() throws Exception {
        var request = request("""
                {"thinking":{"type":"enabled","budget_tokens":2000}}""");

        request.setMaxOutputTokens(3000);

        assertEquals(2000, request.getJson().path("thinking").path("budget_tokens").asInt());
    }

    /**
     * Claude estimates one token per four characters, counting an image as 1000 tokens.
     */
    @Test
    void estimateInputTokensCountsSystemPromptAndMessages() throws Exception {
        assertEquals(3, request("""
                {"system":"12345678","messages":[{"content":"abcd"}]}""").estimateInputTokens());
    }

    @Test
    void estimateInputTokensCountsContentBlocks() throws Exception {
        assertEquals(1002, request("""
                {"messages":[{"content":[{"type":"text","text":"12345678"},{"type":"image"}]}]}""")
                .estimateInputTokens());
    }

    @Test
    void systemPromptRoundTrip() throws Exception {
        var request = request("{}");
        assertEquals("", request.getSystemPrompt());

        request.setSystemPrompts(List.of("one", "two"));
        assertEquals("one\ntwo", request.getSystemPrompt());

        request.removeSystemPrompt();
        assertEquals("", request.getSystemPrompt());
        assertFalse(request.getJson().has("system"));
    }

    private static ClaudeLLMRequest request(String body) throws IOException, URISyntaxException {
        return new ClaudeLLMRequest(exchange(body));
    }

    private static Exchange exchange(String body) throws URISyntaxException {
        return post("http://localhost/v1/messages").json(body).buildExchange();
    }
}
