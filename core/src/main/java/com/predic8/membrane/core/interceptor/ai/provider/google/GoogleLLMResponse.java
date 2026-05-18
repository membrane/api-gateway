package com.predic8.membrane.core.interceptor.ai.provider.google;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.ai.provider.LLMResponse;
import com.predic8.membrane.core.interceptor.ai.store.Usage;
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
        int outputTokens = usage.path("candidatesTokenCount").asInt(0);
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
