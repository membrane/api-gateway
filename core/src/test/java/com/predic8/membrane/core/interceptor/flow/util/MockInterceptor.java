package com.predic8.membrane.core.interceptor.flow.util;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

public class MockInterceptor extends AbstractInterceptor {

    boolean handleRequestCalled;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        handleRequestCalled = true;
        return CONTINUE;
    }

    public boolean isCalled() {
        return handleRequestCalled;
    }
}
