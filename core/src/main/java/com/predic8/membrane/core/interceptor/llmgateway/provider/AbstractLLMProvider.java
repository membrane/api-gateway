package com.predic8.membrane.core.interceptor.llmgateway.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.chatcompletions.ChatCompletionsRequest;

import java.io.IOException;

public abstract class AbstractLLMProvider implements LLMProvider {

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) throws IOException {
        var uri = exchange.getRequest().getUri();
        if (uri.startsWith("/v1/chat/completions")) {
            return new ChatCompletionsRequest(exchange);
        }
        return new BaseLLMRequest(exchange);
    }
}
