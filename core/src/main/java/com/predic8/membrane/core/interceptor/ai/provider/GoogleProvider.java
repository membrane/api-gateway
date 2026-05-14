package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.LLMRequest;
import com.predic8.membrane.core.interceptor.ai.LLMResponse;

import java.util.function.Consumer;

@MCElement( name="google",id = "google-ai-provider")
public class GoogleProvider implements LLMProvider {

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) {
        return new GoogleLLMRequest(exchange);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor) {
        return new GoogleLLMResponse(request, postProcessor);
    }
}
