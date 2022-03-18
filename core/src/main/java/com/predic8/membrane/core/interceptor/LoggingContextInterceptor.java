package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import org.apache.logging.log4j.ThreadContext;

@MCElement(name="logContext")
public class LoggingContextInterceptor extends AbstractInterceptor{
    private final String proxyName = "proxyName";

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        ThreadContext.put(proxyName, exc.getRule().getName());
        return Outcome.CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        ThreadContext.remove(proxyName);
        return Outcome.CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exchange) {
        ThreadContext.remove(proxyName);
    }
}
