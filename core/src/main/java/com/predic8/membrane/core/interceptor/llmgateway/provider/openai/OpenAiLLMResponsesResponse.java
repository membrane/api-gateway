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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.AbstractLLMEvent;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser;
import com.predic8.membrane.core.util.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

public class OpenAiLLMResponsesResponse extends AbstractLLMResponse {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLLMResponsesResponse.class);

    public OpenAiLLMResponsesResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange, postProcessor);
    }

    @Override
    public Usage getUsage() {

        var usage = json.path("usage");

        // For streamed response.completed events
        if (usage.isMissingNode() || usage.isNull()) {
            usage = json.path("response").path("usage");
        }

        var inputTokens = getInputTokens(usage);
        var outputTokens = getOutputTokens(usage);
        var totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        return new Usage(inputTokens, outputTokens, totalTokens);

    }

    @Override
    public Set<String> getTerminalEvents() {
        return Set.of("response.completed", "response.incomplete");
    }

    @Override
    protected void processTerminalEvent(SSEParser.SSEEvent terminal) {
        json = JsonUtil.getJsonObject(terminal.data())
                .orElse(JsonNodeFactory.instance.objectNode()
                        .put("error", "No JSON object response from model."));
    }

    @Override
    public void process(SSEParser.SSEEvent e) {
        log.debug("Event: {}", e.name());
        log.debug("Data: {}", e.data());
        var event = AbstractLLMEvent.create(e);
        log.debug("Event: {}", event);
    }
}
