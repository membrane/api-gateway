package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AbstractLLMResponse;
import com.predic8.membrane.core.interceptor.ai.LLMResponse;

import java.util.function.Consumer;

public class ClaudeLLMResponse extends AbstractLLMResponse {

    public ClaudeLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        super(exchange,postProcessor);
    }

}