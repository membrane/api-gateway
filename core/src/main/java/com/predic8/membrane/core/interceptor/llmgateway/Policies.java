package com.predic8.membrane.core.interceptor.llmgateway;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.ModelInputRequest;

public interface Policies {

    Outcome handleRequest(ModelInputRequest mir, Exchange exc);

    void init(LLMErrorCreator errorCreator);

    int getMaxOutputTokens();
    void setMaxOutputTokens(int maxOutputTokens);

}
