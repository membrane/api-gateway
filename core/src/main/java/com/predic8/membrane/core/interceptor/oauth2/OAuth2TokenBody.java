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
