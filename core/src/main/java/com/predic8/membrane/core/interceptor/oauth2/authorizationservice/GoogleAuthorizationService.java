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

package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.predic8.membrane.annot.MCElement;

@MCElement(name="google", topLevel=false)
public class GoogleAuthorizationService extends AuthorizationService {

    @Override
    public void init() {
        // for backwards compatibility: adds the suffix to the client id. worked without the suffix before but is now needed
        if (!clientId.endsWith(".apps.googleusercontent.com"))
            clientId += ".apps.googleusercontent.com";
        if(scope == null)
            scope = "openid%20email%20profile";
    }

    @Override
    public String getLoginURL(String securityToken, String publicURL, String pathQuery) {
        // This is the URL that is called by the user's web browser
        return "https://accounts.google.com/o/oauth2/auth?"+
                "client_id=" + getClientId() + "&"+
                "response_type=code&"+
                "scope="+scope+"&"+
                "redirect_uri=" + publicURL + "oauth2callback&"+
                "state=security_token%3D" + securityToken + "%26url%3D" + pathQuery
                //+"&login_hint=jsmith@example.com"
                ;
    }

    @Override
    public String getTokenEndpoint() {
        return "https://www.googleapis.com/oauth2/v3/token";
    }

    @Override
    public String getRevocationEndpoint() {
        return "https://accounts.google.com/o/oauth2/revoke";
    }

    @Override
    public String getUserInfoEndpoint() {
        return "https://www.googleapis.com/oauth2/v3/userinfo";
    }

    @Override
    public String getSubject() {
        return "email"; // "login"
    }
}
