package com.predic8.membrane.core.interceptor.ai.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AbstractLLMEvent;
import com.predic8.membrane.core.interceptor.ai.provider.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.ai.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.ai.store.Usage;
import com.predic8.membrane.core.util.http.SSEParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

public class OpenAiLLMResponsesAPIResponse extends AbstractLLMResponse {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLLMResponsesAPIResponse.class);

    public OpenAiLLMResponsesAPIResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
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
    public void process(SSEParser.SSEEvent e) {
        log.debug("Event: {}", e.name());
        log.debug("Data: {}", e.data());
        var event = AbstractLLMEvent.create(e);
        log.debug("Event: {}", event);
    }
}
