package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AbstractAiApiRequest;

import static java.lang.Math.ceil;

public class OpenAiAiRequest extends AbstractAiApiRequest {

    public OpenAiAiRequest(Exchange exchange) {
        super(exchange);
    }

    @Override
    public long estimateInputTokens() {
        return (long) ceil(json.path("input").asText("").length() / 4.0);
    }
}
