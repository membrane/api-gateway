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
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser;

import java.util.Set;
import java.util.function.Consumer;

public class GoogleLLMResponse extends AbstractLLMResponse {

    public GoogleLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange, postProcessor);
    }

    @Override
    public Usage getUsage() {
        var usage = json.path("usageMetadata");

        int inputTokens = usage.path("promptTokenCount").asInt(0);
        int thoughtsTokens = usage.path("thoughtsTokenCount").asInt(0);
        int candidatesTokenCount = usage.path("candidatesTokenCount").asInt(0);
        int outputTokens = thoughtsTokens + candidatesTokenCount;
        int totalTokens = usage.path("totalTokenCount").asInt(inputTokens + outputTokens);

        return new Usage(
                inputTokens,
                outputTokens,
                totalTokens
        );
    }

    @Override
    public Set<String> getTerminalEvents() {
        return Set.of("response.completed","response.incompleted");
    }

    @Override
    public void process(SSEParser.SSEEvent event) {

    }
}
