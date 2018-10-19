package com.predic8.membrane.core.interceptor.oauth2server.provided;

import com.bornium.http.Exchange;
import com.bornium.security.oauth2openid.providers.Session;

public class SessionProvider implements com.bornium.security.oauth2openid.providers.SessionProvider {
    @Override
    public Session getSession(Exchange exchange) {
        return null;
    }
}
