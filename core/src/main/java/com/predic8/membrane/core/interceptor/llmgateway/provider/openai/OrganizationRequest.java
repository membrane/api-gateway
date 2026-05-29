package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;

import java.io.IOException;

public class OrganizationRequest extends AbstractOpenAiLLMRequest {
    public OrganizationRequest(Exchange exchange) throws IOException {
        super(exchange);
    }
}
