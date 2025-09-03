package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

/**
 * See <a
 * href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">OIDC
 * Core 1.0 chapter 9</a>.
 */
public enum ClientAuthorization {
    client_secret_basic,
    client_secret_post
}
