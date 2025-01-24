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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public abstract class AbstractInterceptorWithSession extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractInterceptorWithSession.class);

    SessionManager sessionManager;

    @Override
    public void init() {
        super.init();
        if(sessionManager == null){
            sessionManager = new JwtSessionManager();
        }
        try {
            sessionManager.init(this.router);
        } catch (Exception e) {
            throw new ConfigurationException("Could not init session manager.",e);
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
    public Outcome handleRequest(Exchange exc) {
        Outcome outcome;
        try {
            outcome = handleRequestInternal(exc);
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(),getDisplayName())
                    .addSubType("request")
                    .detail("Error handling request!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        sessionManager.postProcess(exc);
        return outcome;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        try {
            Outcome outcome = handleResponseInternal(exc);
            sessionManager.postProcess(exc);
            return outcome;
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(),getDisplayName())
                    .addSubType("response")
                    .detail("Error handling response!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @MCChildElement()
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
}
