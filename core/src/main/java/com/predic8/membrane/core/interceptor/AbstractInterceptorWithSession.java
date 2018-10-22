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

    protected abstract Outcome handleResponseInternal(Exchange exc) throws Exception;

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        long time = System.nanoTime();
        Outcome outcome = handleResponseInternal(exc);
        sessionManager.postProcess(exc);
        time = System.nanoTime() - time;
        System.out.println("time: " + time/1000000000d);
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
