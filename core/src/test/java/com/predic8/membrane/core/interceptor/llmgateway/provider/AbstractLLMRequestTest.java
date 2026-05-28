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