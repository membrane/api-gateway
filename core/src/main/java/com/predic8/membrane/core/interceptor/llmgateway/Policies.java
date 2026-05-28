package com.predic8.membrane.core.interceptor.llmgateway;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMRequest;

public interface Policies {

    Outcome handleRequest(LLMRequest aiReq, Exchange exc);

    void init(LLMErrorCreator errorCreator);

    int getMaxOutputTokens();
    void setMaxOutputTokens(int maxOutputTokens);

}
