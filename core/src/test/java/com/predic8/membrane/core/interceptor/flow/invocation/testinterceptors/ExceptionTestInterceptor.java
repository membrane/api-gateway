package com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

public class ExceptionTestInterceptor extends AbstractInterceptor {

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        throw new RuntimeException();
    }
}
