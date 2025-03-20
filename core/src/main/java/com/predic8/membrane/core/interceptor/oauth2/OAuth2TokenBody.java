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

import java.util.function.Function;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

public class OAuth2TokenBody {
    private String code;
    private String grantType;
    private String refreshToken;
    private String scope;
    private String redirectUri;

    public static OAuth2TokenBody refreshTokenBodyBuilder(String refreshToken) {
        OAuth2TokenBody r = new OAuth2TokenBody();
        r.grantType = "refresh_token";
        r.refreshToken = refreshToken;
        return r;
    }

    public static OAuth2TokenBody authorizationCodeBodyBuilder(String code) {
        OAuth2TokenBody r = new OAuth2TokenBody();
        r.code = code;
        r.grantType = "authorization_code";
        return r;
    }

    public OAuth2TokenBody scope(String scope) {
        this.scope = scope;
        return this;
    }

    public String build() {
        StringBuilder r = new StringBuilder("grant_type=" + grantType);
        appendParam(r, "refresh_token", refreshToken);
        appendParam(r, "code", code);
        appendParam(r, "redirect_uri", redirectUri);
        appendParam(r, "scope", scope, e -> encode(e, UTF_8));
        return r.toString();
    }

    private void appendParam(StringBuilder sb, String paramName, String paramValue) {
        appendParam(sb, paramName, paramValue, e -> e);
    }

    private void appendParam(StringBuilder sb, String paramName, String paramValue, Function<String, String> encoder) {
        if (paramValue == null)
            return;
        sb.append("&").append(paramName).append("=").append(encoder.apply(paramValue));
    }

    public OAuth2TokenBody redirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }
}
