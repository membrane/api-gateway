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
package com.predic8.membrane.core.interceptor.llmgateway.provider.google;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static com.predic8.membrane.core.interceptor.llmgateway.provider.google.GoogleLLMRequest.X_GOOG_API_KEY;
import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.*;

class GoogleLLMRequestTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # uri                                                  | model
            /v1beta/models/gemini-2.5-pro:generateContent           | gemini-2.5-pro
            /v1beta/models/gemini-2.5-pro%3AgenerateContent         | gemini-2.5-pro
            /v1beta/models/gemini-2.5-pro%3agenerateContent         | gemini-2.5-pro
            /v1beta/models/gemini-2.5-pro                           | gemini-2.5-pro
            """)
    void getModelParsesUri(String uri, String expected) throws Exception {
        assertEquals(expected, request(uri, "{}").getModel());
    }

    @Test
    void getModelIsNullWithoutModelsSegment() throws Exception {
        assertNull(request("/v1beta/chat", "{}").getModel());
    }

    @Test
    void estimateInputTokensCountsSystemInstructionAndParts() throws Exception {
        // systemInstruction 4 + parts 5 = 9 chars -> round(9 / 4.0 * 1.15) = 3
        assertEquals(3, request("""
                {"systemInstruction":{"parts":[{"text":"abcd"}]},"contents":[{"parts":[{"text":"Hello"}]}]}""")
                .estimateInputTokens());
    }

    @Test
    void estimateInputTokensOfEmptyRequestIsOne() throws Exception {
        assertEquals(1, request("{}").estimateInputTokens());
    }

    @Test
    void systemPromptRoundTrip() throws Exception {
        var request = request("{}");
        assertEquals("", request.getSystemPrompt());

        request.setSystemPrompts(List.of("one", "two"));
        assertEquals("one\ntwo", request.getSystemPrompt());
        assertEquals("one\ntwo", request.getJson()
                .path("systemInstruction").path("parts").get(0).path("text").asText());

        request.removeSystemPrompt();
        assertEquals("", request.getSystemPrompt());
        assertFalse(request.getJson().has("systemInstruction"));
    }

    @Test
    void setMaxOutputTokensCreatesGenerationConfig() throws Exception {
        var request = request("{}");
        assertEquals(0, request.getRequestedMaxOutputTokens());

        request.setMaxOutputTokens(42);

        assertEquals(42, request.getRequestedMaxOutputTokens());
    }

    @Test
    void setMaxOutputTokensKeepsExistingGenerationConfig() throws Exception {
        var request = request("""
                {"generationConfig":{"temperature":1,"maxOutputTokens":100}}""");
        assertEquals(100, request.getRequestedMaxOutputTokens());

        request.setMaxOutputTokens(42);

        assertEquals(42, request.getRequestedMaxOutputTokens());
        assertEquals(1, request.getJson().path("generationConfig").path("temperature").asInt());
    }

    @Test
    void apiKeyIsReadFromAndWrittenToGoogleHeader() throws Exception {
        var exchange = post("http://localhost/v1beta/models/m:generateContent").json("{}").buildExchange();
        var request = new GoogleLLMRequest(exchange);
        assertNull(request.getApiKey());

        request.setApiKey("secret");

        assertEquals("secret", request.getApiKey());
        assertEquals("secret", exchange.getRequest().getHeader().getFirstValue(X_GOOG_API_KEY));
    }

    private static GoogleLLMRequest request(String body) throws IOException, URISyntaxException {
        return request("/v1beta/models/gemini-2.5-pro:generateContent", body);
    }

    private static GoogleLLMRequest request(String path, String body) throws IOException, URISyntaxException {
        return new GoogleLLMRequest(post("http://localhost" + path).json(body).buildExchange());
    }
}
