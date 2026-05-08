package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AbstractAiApiResponse;
import com.predic8.membrane.core.interceptor.ai.store.Usage;

public class GoogleAiResponse extends AbstractAiApiResponse {

    public GoogleAiResponse(Exchange exchange) {
        super(exchange);
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
}
