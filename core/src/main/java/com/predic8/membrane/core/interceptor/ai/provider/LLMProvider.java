package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.function.Consumer;

public interface LLMProvider {

    LLMRequest getLLMRequest(Exchange request);
    LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor);
    LLMErrorCreator getErrorCreator();

}
