package com.predic8.membrane.core.interceptor.oauth2;

import static com.predic8.membrane.core.interceptor.oauth2.OAuth2TestUtil.useJWTForAccessTokensAndRefreshTokens;

public class OAuth2AuthorizationServerInterceptorNormalJWTTest extends OAuth2AuthorizationServerInterceptorNormalTest {
    @Override
    public void configureOASI(OAuth2AuthorizationServerInterceptor oasi) {
        useJWTForAccessTokensAndRefreshTokens(oasi);
    }
}
