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

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.request.ParameterizedRequest;

import java.io.IOException;

public abstract class TokenRequest extends ParameterizedRequest {

    public static final String ACCESS_TOKEN = "access_token";

    protected String scope;
    protected String token;
    protected String idToken;

    public TokenRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        super(authServer, exc);
    }

    protected String getTokenJSONResponse(String scope, String token, String idToken) throws IOException {
        String json;
        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();
            gen.writeObjectField("access_token", token);
            gen.writeObjectField("token_type", authServer.getTokenGenerator().getTokenType());
            //gen.writeObjectField("expires_in", "null"); // TODO is optional but maybe useful?
            if(scope != null && !scope.isEmpty())
                gen.writeObjectField(ParamNames.SCOPE, scope);
            if (idToken != null && !idToken.isEmpty())
                gen.writeObjectField(ParamNames.ID_TOKEN, idToken);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }
        return json;
    }
}
