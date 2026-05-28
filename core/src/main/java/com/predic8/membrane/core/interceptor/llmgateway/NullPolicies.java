package com.predic8.membrane.core.interceptor.llmgateway;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMErrorCreator;
import com.predic8.membrane.core.interceptor.llmgateway.provider.LLMRequest;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

public class NullPolicies implements Policies {

    @Override
    public Outcome handleRequest(LLMRequest aiReq, Exchange exc) {
        return CONTINUE;
    }

    @Override
    public void init(LLMErrorCreator errorCreator) {

    }

    @Override
    public int getMaxOutputTokens() {
        return 0;
    }

    @Override
    public void setMaxOutputTokens(int maxOutputTokens) {
    }
}

