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

package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the character based token estimate shared by all OpenAI style requests. The expected values
 * follow from counting the relevant characters and applying max(1, round(chars / 4.0 * 1.15)).
 */
class AbstractOpenAiLLMRequestTest {

    private static final class TestRequest extends AbstractOpenAiLLMRequest {
        private TestRequest(Exchange exchange) throws IOException {
            super(exchange);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            # body                                            | expected
            {}                                                | 1
            {"messages":[{"role":"user","content":"Hello"}]}   | 3
            {"input":"12345678"}                              | 2
            {"instructions":"abcd"}                           | 1
            {"system":"abcd"}                                 | 1
            {"input":[{"type":"text","text":"Hello"}]}        | 1
            {"input":{"content":"abcdefgh"}}                  | 2
            {"tools":[{"type":"function"}]}                   | 6
            {"functions":[{"type":"function"}]}               | 6
            """)
    void estimateInputTokens(String body, long expected) throws Exception {
        assertEquals(expected, request(body).estimateInputTokens());
    }

    /**
     * The parts of a request add up: messages, system, instructions and tools are all counted.
     */
    @org.junit.jupiter.api.Test
    void estimateInputTokensSumsAllSections() throws Exception {
        // content 5 + role 4 + system 4 + instructions 4 = 17 chars -> round(17 / 4.0 * 1.15) = 5
        assertEquals(5, request("""
                {"messages":[{"role":"user","content":"Hello"}],"system":"abcd","instructions":"abcd"}""")
                .estimateInputTokens());
    }

    private static TestRequest request(String body) throws IOException, URISyntaxException {
        return new TestRequest(post("http://localhost/v1/responses").json(body).buildExchange());
    }
}
