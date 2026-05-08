package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AiApiRequest;
import com.predic8.membrane.core.interceptor.ai.AiApiResponse;

@MCElement( name="google",id = "google-ai-provider")
public class Google implements AiProvider {

    @Override
    public AiApiRequest getAiApiRequest(Exchange exchange) {
        return new GoogleAiRequest(exchange);
    }

    @Override
    public AiApiResponse getAiApiResponse(Exchange request) {
        return new GoogleAiResponse(request);
    }
}
