package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name = "paddingHeader")
public class PaddingHeaderInterceptor extends AbstractInterceptor {
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return super.handleRequest(exc);
    }
}
