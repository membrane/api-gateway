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

public class OpenAiLLMResponse extends AbstractLLMResponse {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLLMResponse.class);

    public OpenAiLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange,postProcessor);
    }

    @Override
    public Usage getUsage() {

        int inputTokens = 0;
        int outputTokens = 0;
        int totalTokens = 0;

        // Responses API
        if (!json.path("response").isMissingNode()) {
            var usage = json.path("response").path("usage");

            getInputTokens(usage);
            getOutputTokens(usage);
            usage.path("total_tokens").asInt(inputTokens + outputTokens);
        } else {
            // Older chat completions API
            inputTokens = json.path("usage").path("prompt_tokens").asInt(0);
            outputTokens = json.path("usage").path("completion_tokens").asInt(0);
            totalTokens = json.path("total_tokens").asInt(inputTokens + outputTokens);

        }

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
    public void process(SSEParser.SSEEvent e) {
        log.debug("Event: {}", e.name());
        log.debug("Data: {}", e.data());
        var event = AbstractLLMEvent.create(e);
        System.out.println(event);

        var json = event.getJson();
        if (!json.path("usage").isNull()) {

        }
    }
}
