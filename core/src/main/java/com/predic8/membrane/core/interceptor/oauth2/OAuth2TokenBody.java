/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.util.URLParamUtil;

public class OAuth2TokenBody {
    private String code;
    private String grantType;
    private String refreshToken;
    private String scope;
    private String redirectUri;
    private String codeVerifier;
    private String clientId;
    private String clientSecret;
    private String clientAssertion;
    private String clientAssertionType;

    private OAuth2TokenBody() {}

    public static OAuth2TokenBody refreshTokenBodyBuilder(String refreshToken) {
        OAuth2TokenBody r = new OAuth2TokenBody();
        r.grantType = "refresh_token";
        r.refreshToken = refreshToken;
        return r;
    }

    public static OAuth2TokenBody authorizationCodeBodyBuilder(String code, String verifier) {
        OAuth2TokenBody r = new OAuth2TokenBody();
        r.code = code;
        r.grantType = "authorization_code";
        r.codeVerifier = verifier;
        return r;
    }

    public OAuth2TokenBody scope(String scope) {
        this.scope = scope;
        return this;
    }

    public OAuth2TokenBody clientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public OAuth2TokenBody clientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public OAuth2TokenBody clientAssertion(String type, String assertion) {
        this.clientAssertionType = type;
        this.clientAssertion = assertion;
        return this;
    }

    public String build() {
        return URLParamUtil.createQueryStringOmitNullValues(
                "grant_type", grantType,
                "refresh_token", refreshToken,
                "code", code,
                "redirect_uri", redirectUri,
                "scope", scope,
                "code_verifier", codeVerifier,
                "client_id", clientId,
                "client_secret", clientSecret,
                "client_assertion_type", clientAssertionType,
                "client_assertion", clientAssertion
        );
    }

    public OAuth2TokenBody redirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }
}
