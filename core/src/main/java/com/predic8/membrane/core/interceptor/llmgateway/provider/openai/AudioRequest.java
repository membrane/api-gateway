package com.predic8.membrane.core.interceptor.llmgateway.provider.openai;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.llmgateway.provider.AbstractModelInputRequest;

import java.io.IOException;

public class AudioRequest extends AbstractModelInputRequest {
    public AudioRequest(Exchange exchange) throws IOException {
        super(exchange);
    }
}
