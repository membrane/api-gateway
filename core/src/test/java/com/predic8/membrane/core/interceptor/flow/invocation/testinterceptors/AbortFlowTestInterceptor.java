package com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;

public class AbortFlowTestInterceptor extends AbstractInterceptor {

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(Response.ok().body(exc.getRequest().getBody().getContent()).build());
        return ABORT;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return ABORT;
    }
}
