package com.predic8.membrane.core.interceptor.oauth2.client.b2c;

import com.predic8.membrane.core.interceptor.oauth2.client.FakeSyncSessionStoreManager;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;

public class SyncB2CResourceTest extends OAuth2ResourceB2CTest {
    @Override
    protected void configureSessionManager(OAuth2Resource2Interceptor oauth2) {
        oauth2.setSessionManager(new FakeSyncSessionStoreManager());
    }
}
