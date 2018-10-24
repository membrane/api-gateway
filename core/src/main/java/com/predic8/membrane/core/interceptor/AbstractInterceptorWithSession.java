package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;

public abstract class AbstractInterceptorWithSession extends AbstractInterceptor {

    SessionManager sessionManager;

    @Override
    public void init() throws Exception {
        if(sessionManager == null)
            sessionManager = new JwtSessionManager();
    }

    /**
     * Do not override handleRequest like usual but use this method to implement your own handle request logic
     * @param exc
     * @return
     * @throws Exception
     */
    protected abstract Outcome handleRequestInternal(Exchange exc) throws Exception;

    /**
     * Do not override handleResponse like usual but use this method to implement your own handle response logic
     * @param exc
     * @return
     * @throws Exception
     */
    protected abstract Outcome handleResponseInternal(Exchange exc) throws Exception;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        Outcome outcome = handleRequestInternal(exc);
        sessionManager.postProcess(exc);
        return outcome;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        Outcome outcome = handleResponseInternal(exc);
        sessionManager.postProcess(exc);
        return outcome;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @MCChildElement(order = 0)
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
}
