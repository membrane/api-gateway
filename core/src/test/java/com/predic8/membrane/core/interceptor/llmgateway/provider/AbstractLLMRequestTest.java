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

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractLLMRequestTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer test-api-key",
            "bearer test-api-key",
            "BEARER test-api-key",
            "bEaReR test-api-key"
    })
    void getApiKeyAcceptsBearerCaseInsensitive(String authorization) throws URISyntaxException {
        var request = new TestLLMRequest(post("http://localhost/chat/completions")
                .header("Authorization", authorization)
                .json("{}")
                .buildExchange());

        assertEquals("test-api-key", request.getApiKey());
    }

    private static class TestLLMRequest extends AbstractLLMRequest {

        TestLLMRequest(Exchange exchange) {
            super(exchange);
        }

        @Override
        public long getRequestedMaxOutputTokens() {
            return -1;
        }

        @Override
        public void setMaxOutputTokens(int maxOutputTokens) {
        }

        @Override
        public long estimateInputTokens() {
            return 0;
        }

        @Override
        public String getSystemPrompt() {
            return null;
        }

        @Override
        public boolean isChatCompletion() {
            return false;
        }

        @Override
        public void setSystemPrompt(String systemPrompt) {

        }

        @Override
        public void removeSystemPrompt() {

        }
    }
}