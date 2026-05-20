package com.predic8.membrane.core.interceptor.ai.provider.openai;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.ai.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.ai.provider.LLMResponse;

import java.util.function.Consumer;

/**
 * @description OpenAI provider configuration
 * Use to configure a LLM gateway to use the OpenAI API
 */
@MCElement( name="openai")
public class OpenAIProvider implements LLMProvider {

    boolean isResponsesAPI;

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) {
        isResponsesAPI = isResponsesApi(exchange);
        if (isResponsesAPI) {
            return new OpenAiLLMResponsesRequest(exchange);
        }

        return new OpenAiLLMChatCompletionsRequest(exchange);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange exchange, Consumer<LLMResponse> postProcessor) {
        if (isResponsesAPI) {
            return new OpenAiLLMResponsesResponse(exchange,postProcessor);
        }
        return new OpenAiChatCompletionsLLMResponse(exchange, postProcessor);
    }

    static boolean isResponsesApi(Exchange exchange) {
        return exchange.getRequest().getUri().startsWith("/v1/responses");
    }
}
