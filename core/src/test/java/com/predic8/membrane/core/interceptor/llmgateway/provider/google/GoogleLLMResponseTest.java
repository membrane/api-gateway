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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMResponseTest;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

class GoogleLLMResponseTest extends AbstractLLMResponseTest {

    @Override
    protected String url() {
        return "http://localhost/v1beta/models/gemini-2.5-pro:streamGenerateContent";
    }

    @Override
    protected AbstractLLMResponse createResponse(Exchange exchange) {
        return new GoogleLLMResponse(exchange, processed::add);
    }

    /**
     * The Gemini stream has no terminal event, so the usage is only complete once the body ends.
     */
    @Test
    void usageOfStreamedResponseIsReportedOnceAtEndOfBody() throws URISyntaxException {
        stream("""
                data: {"candidates":[{"content":{"parts":[{"text":"Hi"}]}}],"usageMetadata":{"promptTokenCount":11}}

                data: {"candidates":[{"content":{"parts":[{"text":"!"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":7,"totalTokenCount":18}}

                """);

        assertUsage(new Usage(11, 7, 18));
    }

    @Test
    void thoughtTokensOfStreamedResponseCountAsOutput() throws URISyntaxException {
        stream("""
                data: {"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":7,"thoughtsTokenCount":5,"totalTokenCount":22}}

                """);

        assertUsage(new Usage(10, 12, 22));
    }

    @Test
    void usageOfNonStreamedResponseIsReportedOnce() throws URISyntaxException {
        newResponse(withJsonResponse("""
                {"usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":6,"totalTokenCount":11}}"""));

        assertUsage(new Usage(5, 6, 11));
    }
}
