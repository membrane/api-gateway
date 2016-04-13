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

package com.predic8.membrane.core.interceptor.oauth2.request.tokenrequest;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.request.ParameterizedRequest;
import com.predic8.membrane.core.util.URLParamUtil;

import java.util.Map;

public class TokenFlowDecider {

    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String PASSWORD = "password";
    private static final String CLIENT_CREDENTIALS = "client_credentials";

    Map<String,String> params;
    ParameterizedRequest flow;

    public TokenFlowDecider(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        params = URLParamUtil.getParams(authServer.getRouter().getUriFactory(), exc);
        flow = new ErrorFlow(authServer,exc);
        if(getGrantType() == null)
            return;
        if(getGrantType().equals(AUTHORIZATION_CODE)) {
            flow = new AuthorizationCodeFlow(authServer, exc);
            authServer.getStatistics().codeFlow();
            return;
        }
        if(getGrantType().equals(PASSWORD)){
            flow = new PasswordFlow(authServer,exc);
            authServer.getStatistics().passwordFlow();
            return;
        }
        if(getGrantType().equals(CLIENT_CREDENTIALS)){
            flow = new CredentialsFlow(authServer,exc);
            authServer.getStatistics().clientCredentialsFlow();
            return;
        }

    }

    private String getGrantType() {
        return params.get(ParamNames.GRANT_TYPE);
    }

    public ParameterizedRequest getFlow(){
        return flow;
    }

}
