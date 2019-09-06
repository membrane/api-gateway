/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;

public abstract class AbstractInterceptorWithSession extends AbstractInterceptor {

    SessionManager sessionManager;

    @Override
    public void init() throws Exception {
        if(sessionManager == null){
            sessionManager = new JwtSessionManager();
            sessionManager.init(this.router);
        }
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
