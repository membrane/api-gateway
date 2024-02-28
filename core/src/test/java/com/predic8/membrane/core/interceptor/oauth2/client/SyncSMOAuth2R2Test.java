package com.predic8.membrane.core.interceptor.oauth2.client;

import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;

public class SyncSMOAuth2R2Test extends OAuth2ResourceTest {
    @Override
    protected void configureSessionManager(OAuth2Resource2Interceptor oauth2) {
        oauth2.setSessionManager(new FakeSyncSessionStoreManager());
    }
}
