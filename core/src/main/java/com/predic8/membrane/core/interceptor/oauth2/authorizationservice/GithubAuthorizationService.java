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

@MCElement(name="github", topLevel=false)
public class GithubAuthorizationService extends AuthorizationService {
    @Override
    public void init() {
        if(scope == null)
            scope = "openid%20email%20profile";
    }

    @Override
    public String getIssuer() {
        return "https://github.com"; // github is no openid provider, so this doesn't work
    }

    @Override
    public String getJwksEndpoint() {
        return "";
    }

    @Override
    public String getLoginURL(String callbackURL) {
        return "https://github.com/login/oauth/authorize?"+
                "client_id=" + getClientId() + "&"+
                "response_type=code&"+
                "scope="+scope+"&"+
                "redirect_uri=" + callbackURL
                //+"&login_hint=jsmith@example.com"
                ;
    }

    @Override
    public String getUserInfoEndpoint() {
        return "https://api.github.com/user";
    }

    @Override
    public String getSubject() {
        return "login";
    }

    @Override
    public String getTokenEndpoint() {
        return "https://github.com/login/oauth/access_token";
    }

    @Override
    public String getRevocationEndpoint() {
        return null;
    }

    @Override
    public String getEndSessionEndpoint() throws Exception {
        return null;
    }
}
