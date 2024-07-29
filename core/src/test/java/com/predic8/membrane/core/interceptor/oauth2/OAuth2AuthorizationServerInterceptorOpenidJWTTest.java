package com.predic8.membrane.core.interceptor.oauth2;

import static com.predic8.membrane.core.interceptor.oauth2.OAuth2TestUtil.configureJWT;

public class OAuth2AuthorizationServerInterceptorOpenidJWTTest extends OAuth2AuthorizationServerInterceptorOpenidTest {
    @Override
    public void configureOASI(OAuth2AuthorizationServerInterceptor oasi) {
        configureJWT(oasi);
    }
}
