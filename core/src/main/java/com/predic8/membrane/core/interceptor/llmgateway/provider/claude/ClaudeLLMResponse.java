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

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.llmgateway.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser.SSEEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

public class ClaudeLLMResponse extends AbstractLLMResponse {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLLMResponse.class);

    private Usage usage;

    private final StringBuffer inputJson = new StringBuffer();

    private String tool;

    public ClaudeLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange,postProcessor);
    }

    @Override
    public Set<String> getTerminalEvents() {
        return Set.of("message_stop");
    }

    @Override
    public void process(SSEEvent event) {
        log.debug("Event: {}", event);

        if ("content_block_start".equals(event.name())) {
            var cbs = ContentBlockStart.from(event.json());
            if (cbs.getToolUse() != null) {
                tool = cbs.getToolUse().getName();
            }
        }
        if ("message_delta".equals(event.name())) {
            var md = MessageDelta.from(event.json());
            log.debug("Message delta: {}", md);
            if (md.getUsage() != null) {
                usage = md.getUsage();
                if (tool != null)
                    log.debug("Tool {} with {}", tool, inputJson.toString());
            }
        }
        if ("content_block_delta".equals(event.name())) {
            var cbd = ContentBlockDelta.from(event.json());
            if (cbd.isInputJsonDelta()) {
                inputJson.append(cbd.getPartialJson());
            }
        }
    }

    Usage extractUsage() {

        var usage = json.path("usage");

        var inputTokens = getInputTokens(usage);
        var outputTokens = getOutputTokens(usage);
        var totalTokens = inputTokens + outputTokens;
        return new Usage(inputTokens, outputTokens, totalTokens);

    }

    protected static int getOutputTokens(JsonNode usage) {
        return usage.path("output_tokens").asInt(0);
    }

    protected static int getInputTokens(JsonNode usage) {
        return usage.path("input_tokens").asInt(0);
    }

    @Override
    public Usage getUsage() {
        if (usage != null)
            return usage;
        return usage = extractUsage();
    }

}