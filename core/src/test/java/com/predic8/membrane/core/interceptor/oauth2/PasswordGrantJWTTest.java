package com.predic8.membrane.core.interceptor.oauth2;

import static com.predic8.membrane.core.interceptor.oauth2.OAuth2TestUtil.configureJWT;

public class PasswordGrantJWTTest extends PasswordGrantTest {
    @Override
    public void configureOASI(OAuth2AuthorizationServerInterceptor oasi) {
        configureJWT(oasi);
    }
}
