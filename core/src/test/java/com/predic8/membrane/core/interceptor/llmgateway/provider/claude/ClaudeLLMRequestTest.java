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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URISyntaxException;

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
     * The shared estimate: max(1, round(chars / 4.0 * 1.15)), an image counting as 4000 characters.
     */
    @Test
    void estimateInputTokensCountsSystemPromptAndMessages() throws Exception {
        // 12 chars -> 3
        assertEquals(3, request("""
                {"system":"12345678","messages":[{"content":"abcd"}]}""").estimateInputTokens());
    }

    @Test
    void estimateInputTokensCountsContentBlocks() throws Exception {
        // 8 + 4000 chars -> 1152
        assertEquals(1152, request("""
                {"messages":[{"content":[{"type":"text","text":"12345678"},{"type":"image"}]}]}""")
                .estimateInputTokens());
    }

    /**
     * Every input Anthropic bills for has to show up in the estimate, otherwise maxInputTokens and the
     * user's budget can be passed by moving the text into a structure that is not counted. 4000
     * characters are at least 1000 tokens at four characters per token.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            system as text blocks       | {"system":[{"type":"text","text":"%s"}],"messages":[{"role":"user","content":"Hi"}]}
            tool definitions            | {"tools":[{"name":"t","description":"%s","input_schema":{"type":"object"}}],"messages":[{"role":"user","content":"Hi"}]}
            tool result as text         | {"messages":[{"role":"user","content":[{"type":"tool_result","tool_use_id":"t1","content":"%s"}]}]}
            tool result as text blocks  | {"messages":[{"role":"user","content":[{"type":"tool_result","tool_use_id":"t1","content":[{"type":"text","text":"%s"}]}]}]}
            tool use input              | {"messages":[{"role":"assistant","content":[{"type":"tool_use","id":"t1","name":"t","input":{"q":"%s"}}]}]}
            plain text document         | {"messages":[{"role":"user","content":[{"type":"document","source":{"type":"text","media_type":"text/plain","data":"%s"}}]}]}
            base64 PDF document         | {"messages":[{"role":"user","content":[{"type":"document","source":{"type":"base64","media_type":"application/pdf","data":"%s"}}]}]}
            thinking block              | {"messages":[{"role":"assistant","content":[{"type":"thinking","thinking":"%s","signature":"s"}]}]}
            document title              | {"messages":[{"role":"user","content":[{"type":"document","title":"%s","source":{"type":"text","media_type":"text/plain","data":""}}]}]}
            document context            | {"messages":[{"role":"user","content":[{"type":"document","context":"%s","source":{"type":"text","media_type":"text/plain","data":""}}]}]}
            """)
    void estimateInputTokensCountsEveryBilledInput(String structure, String template) throws Exception {
        var estimate = request(template.formatted("x".repeat(4000))).estimateInputTokens();

        assertTrue(estimate >= 1000, "%s: estimated %d tokens for 4000 characters".formatted(structure, estimate));
    }

    /**
     * Anthropic bills a PDF by its pages, at least about 1500 tokens for one page. The page count is not
     * in the request, so every PDF counts for one page.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            base64 | {"messages":[{"role":"user","content":[{"type":"document","source":{"type":"base64","media_type":"application/pdf","data":"JVBERi0="}}]}]}
            url    | {"messages":[{"role":"user","content":[{"type":"document","source":{"type":"url","url":"https://example.com/a.pdf"}}]}]}
            """)
    void pdfCountsForAtLeastOnePage(String source, String json) throws Exception {
        var estimate = request(json).estimateInputTokens();

        assertTrue(estimate >= 1500, "%s: estimated %d tokens for a PDF".formatted(source, estimate));
    }

    @Test
    void systemPromptRoundTrip() throws Exception {
        var request = request("{}");

        request.overwriteSystemPrompt("one");
        assertEquals("one", request.getJson().path("system").asText());

        request.removeSystemPrompt();
        assertFalse(request.getJson().has("system"));
    }

    /**
     * Anthropic rejects an empty text block, so an empty prompt must leave the client's system prompt
     * as it was sent.
     */
    @ParameterizedTest(name = "{0} to {1}")
    @CsvSource(delimiter = '|', value = {
            "prepend | {}",
            "append  | {}",
            "prepend | {\"system\":\"client\"}",
            "append  | {\"system\":\"client\"}",
            "prepend | {\"system\":[{\"type\":\"text\",\"text\":\"client\"}]}",
            "append  | {\"system\":[{\"type\":\"text\",\"text\":\"client\"}]}",
    })
    void emptyPromptLeavesSystemPromptUnchanged(String action, String body) throws Exception {
        final var request = request(body);
        final var before = request.getJson().deepCopy();

        if ("prepend".equals(action))
            request.prependSystemPrompt("");
        else
            request.appendSystemPrompt("");

        assertEquals(before, request.getJson());
    }

    private static ClaudeLLMRequest request(String body) throws IOException, URISyntaxException {
        return new ClaudeLLMRequest(exchange(body));
    }

    private static Exchange exchange(String body) throws URISyntaxException {
        return post("http://localhost/v1/messages").json(body).buildExchange();
    }
}
