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
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractModelInputRequest;

import java.io.IOException;

import static com.predic8.membrane.core.interceptor.llmgateway.provider.TokenEstimator.*;

public abstract class AbstractOpenAiLLMRequest extends AbstractModelInputRequest {

    public AbstractOpenAiLLMRequest(Exchange exchange) throws IOException {
        super(exchange);
    }

    @Override
    public long estimateInputTokens() {

        long chars = countText(json.path("input"));

        chars += estimateChatCompletions();

        // system instructions: "system" (chat completions) or "instructions" (responses API)
        chars += countText(json.path("system"));
        chars += countText(json.path("instructions"));

        // tools/functions contribute significantly
        chars += countJsonSize(json.path("tools"));
        chars += countJsonSize(json.path("functions"));

        return charsToTokens(chars);
    }

    private long estimateChatCompletions() {
        long chars = 0;
        // Chat Completions API
        var messages = json.path("messages");
        if (messages.isArray()) {
            for (var message : messages) {
                chars += countText(message.path("content"));
                // roles also consume tokens
                chars += message.path("role").asText("").length();
            }
        }
        return chars;
    }
}
