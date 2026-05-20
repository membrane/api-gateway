package com.predic8.membrane.core.interceptor.ai.provider.google;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.ai.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.ai.provider.LLMResponse;

import java.util.function.Consumer;

/**
 * @description Google AI provider configuration
 * Use to configure a LLM gateway to use the Google LLM API
 */
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
