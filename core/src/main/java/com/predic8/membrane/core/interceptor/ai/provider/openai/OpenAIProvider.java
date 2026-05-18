package com.predic8.membrane.core.interceptor.ai.provider.openai;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.ai.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.ai.provider.LLMResponse;

import java.util.function.Consumer;

@MCElement( name="openai")
public class OpenAIProvider implements LLMProvider {

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) {
        return new OpenAiLLMRequest(exchange);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor) {
        return new OpenAiLLMResponse(request, postProcessor);
    }
}
