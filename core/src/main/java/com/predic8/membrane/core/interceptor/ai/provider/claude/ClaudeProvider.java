package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.ai.provider.LLMProvider;
import com.predic8.membrane.core.interceptor.ai.provider.LLMRequest;
import com.predic8.membrane.core.interceptor.ai.provider.LLMResponse;

import java.util.function.Consumer;

/**
 * @description (Experimental) Anthroic Claude provider configuration
 * Use to configure a LLM gateway to use the anthropic API
 */
@MCElement( name="claude")
public class ClaudeProvider implements LLMProvider {

    @Override
    public LLMRequest getLLMRequest(Exchange exchange) {
        return new ClaudeLLMRequest(exchange);
    }

    @Override
    public LLMResponse getLLMResponse(Exchange request, Consumer<LLMResponse> postProcessor) {
        return new ClaudeLLMResponse(request, postProcessor);
    }

    @Override
    public LLMErrorCreator getErrorCreator() {
        return new ClaudeErrorCreator();
    }
}
