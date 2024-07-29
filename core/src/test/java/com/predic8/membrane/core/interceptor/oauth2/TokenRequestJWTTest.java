package com.predic8.membrane.core.interceptor.oauth2;

import static com.predic8.membrane.core.interceptor.oauth2.OAuth2TestUtil.configureJWT;

public class TokenRequestJWTTest extends TokenRequestTest {
    @Override
    public void configureOASI(OAuth2AuthorizationServerInterceptor oasi) {
        configureJWT(oasi);
    }
}
