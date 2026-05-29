package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMRequest;

public class ImagesRequest extends AbstractLLMRequest {

    public ImagesRequest(Exchange exchange) {
        super(exchange);
    }
}
