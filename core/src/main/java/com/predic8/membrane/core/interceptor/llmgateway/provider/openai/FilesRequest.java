package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractLLMRequest;

public class FilesRequest extends AbstractLLMRequest {

    public FilesRequest(Exchange exchange) {
        super(exchange);
    }

}
