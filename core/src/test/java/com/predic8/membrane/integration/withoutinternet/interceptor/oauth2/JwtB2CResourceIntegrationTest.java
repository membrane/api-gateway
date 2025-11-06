package com.predic8.membrane.integration.withoutinternet.interceptor.oauth2;

import com.predic8.membrane.core.interceptor.session.SessionManager;

public class JwtB2CResourceIntegrationTest extends OAuth2ResourceB2CIntegrationTest {
    @Override
    protected SessionManager createSessionManager() {
        return null;
    }
}
