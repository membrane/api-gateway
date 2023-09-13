package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;

@MCElement(name = "paddingHeader")
public class PaddingHeaderInterceptor extends AbstractInterceptor{
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return super.handleRequest(exc);
    }
}
