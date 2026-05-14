package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.ai.LLMResponse;
import com.predic8.membrane.core.interceptor.ai.store.Usage;

import java.util.function.Consumer;

public class OpenAiLLMResponse extends AbstractLLMResponse {

    public OpenAiLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange,postProcessor);
    }

    @Override
    public Usage getUsage() {

        var usage = json.path("response").path("usage");

        int inputTokens = getInputTokens(usage);
        int outputTokens = getOutputTokens(usage);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);

        return new Usage(
                inputTokens,
                outputTokens,
                totalTokens
        );
    }

}
