/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.flows;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;

public abstract class OAuth2Flow {
    protected OAuth2AuthorizationServerInterceptor authServer;
    protected Exchange exc;
    protected SessionManager.Session session;


    public OAuth2Flow(OAuth2AuthorizationServerInterceptor authServer, Exchange exc, SessionManager.Session s) {
        this.authServer = authServer;
        this.exc = exc;
        this.session = s;
    }

    public abstract Outcome getResponse() throws Exception;

    protected String stateQuery(String state) {
        return state == null ? "" : "&state=" + state;
    }
}
