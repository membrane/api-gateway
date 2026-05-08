package com.predic8.membrane.core.interceptor.ai.provider;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ai.AiApiRequest;
import com.predic8.membrane.core.interceptor.ai.AiApiResponse;

public interface AiProvider {

    AiApiRequest getAiApiRequest(Exchange request);
    AiApiResponse getAiApiResponse(Exchange request);

}
