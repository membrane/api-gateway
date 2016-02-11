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

package com.predic8.membrane.core.interceptor.oauth2;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@MCElement(name="google", topLevel=false)
public class GoogleAuthorizationService extends AuthorizationService {

    private JsonFactory factory;
    private GoogleIdTokenVerifier verifier;

    @Override
    public void init() {
        factory = new JacksonFactory();
        verifier = new GoogleIdTokenVerifier(new ApacheHttpTransport(), factory);
    }

    @Override
    public String getLoginURL(String securityToken, String publicURL, String pathQuery) {
        // This is the URL that is called by the user's web browser
        return "https://accounts.google.com/o/oauth2/auth?"+
                "client_id=" + getClientId() + ".apps.googleusercontent.com&"+
                "response_type=code&"+
                "scope=openid%20email&"+
                "redirect_uri=" + publicURL + "oauth2callback&"+
                "state=security_token%3D" + securityToken + "%26url%3D" + pathQuery
                //+"&login_hint=jsmith@example.com"
                ;
    }

    @Override
    public void authorize(String code, String publicURL, Session session) throws Exception {
        Exchange e = new Request.Builder()
                .post("https://www.googleapis.com/oauth2/v3/token")
                .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(Header.HOST, "www.googleapis.com")
                .header(Header.ACCEPT, "*/*")
                .header(Header.USER_AGENT, Constants.USERAGENT)
                .body(    "code=" + code
                        + "&" + "client_id=" + getClientId() + ".apps.googleusercontent.com"
                        + "&" + "client_secret=" + getClientSecret()
                        + "&" + "redirect_uri=" + publicURL + "oauth2callback"
                        + "&" + "grant_type=authorization_code")
                .buildExchange();

        LogInterceptor logi = null;
        if (log.isDebugEnabled()) {
            logi = new LogInterceptor();
            logi.setHeaderOnly(false);
            logi.handleRequest(e);
        }

        Response response = httpClient.call(e).getResponse();

        if (response.getStatusCode() != 200) {
            response.getBody().read();
            throw new RuntimeException("Google Authentication server returned " + response.getStatusCode() + ".");
        }

        if (log.isDebugEnabled())
            logi.handleResponse(e);

        HashMap<String, String> json = Util.parseSimpleJSONResponse(response);

        if (!json.containsKey("id_token"))
            throw new RuntimeException("No id_token received.");

        GoogleIdToken idToken = GoogleIdToken.parse(factory, json.get("id_token"));
        if (idToken == null)
            throw new RuntimeException("Token cannot be parsed");

        if (!verifier.verify(idToken) ||
                !idToken.verifyAudience(Collections.singletonList(getClientId() + ".apps.googleusercontent.com")))
            throw new RuntimeException("Invalid token");

        Map<String, String> userAttributes = session.getUserAttributes();
        synchronized (userAttributes) {
            userAttributes.put("headerX-Authenticated-Email", idToken.getPayload().getEmail());
        }
    }
}
