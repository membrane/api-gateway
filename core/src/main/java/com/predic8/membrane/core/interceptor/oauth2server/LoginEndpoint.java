/*
 * Copyright 2019 predic8 GmbH, www.predic8.com
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
package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.http.Exchange;
import com.bornium.http.ResponseBuilder;
import com.bornium.security.oauth2openid.server.ServerServices;
import com.bornium.security.oauth2openid.server.endpoints.Endpoint;
import com.predic8.membrane.core.interceptor.authentication.session.LoginDialog;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;

public class LoginEndpoint extends Endpoint {

    LoginDialog2 loginDialog;

    public LoginEndpoint(ServerServices serverServices, UserDataProvider userDataProvider, com.predic8.membrane.core.interceptor.session.SessionManager sessionManager, String loginDialogLocation, String loginPath, String... paths) {
        super(serverServices, paths);
        loginDialog = new LoginDialog2(userDataProvider,null,sessionManager,null,loginDialogLocation,loginPath,true,null);
    }

    @Override
    public void invokeOn(Exchange exchange) throws Exception {
        com.predic8.membrane.core.exchange.Exchange exc = Convert.convertToMembraneExchange(exchange);
        loginDialog.handleLoginRequest(exc);
        exchange = Convert.convertFromMembraneExchange(exc);
    }

    @Override
    public String getScope(Exchange exchange) throws Exception {
        return null;
    }
}
