package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractModelInputRequestTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer test-api-key",
            "bearer test-api-key",
            "BEARER test-api-key",
            "bEaReR test-api-key"
    })
    void getApiKeyAcceptsBearerCaseInsensitive(String authorization) throws URISyntaxException, IOException {
        var request = new TestLLMRequest(post("http://localhost/chat/completions")
                .header("Authorization", authorization)
                .json("{}")
                .buildExchange());

        assertEquals("test-api-key", request.getApiKey());
    }

    private static class TestLLMRequest extends AbstractModelInputRequest implements ModelInputRequest {

        TestLLMRequest(Exchange exchange) throws IOException {
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
        public void setSystemPrompts(List<String> prompts) {

        }

        @Override
        public void removeSystemPrompt() {

        }
    }
}