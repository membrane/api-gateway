package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.security.oauth2openid.providers.ClientDataProvider;
import com.bornium.security.oauth2openid.providers.SessionProvider;
import com.bornium.security.oauth2openid.providers.UserDataProvider;
import com.bornium.security.oauth2openid.server.ProvidedServices;

import java.util.Set;

public class MembraneProvidedServices implements ProvidedServices {
    @Override
    public SessionProvider getSessionProvider() {
        return null;
    }

    @Override
    public ClientDataProvider getClientDataProvider() {
        return null;
    }

    @Override
    public UserDataProvider getUserDataProvider() {
        return null;
    }

    @Override
    public String getIssuer() {
        return null;
    }

    @Override
    public Set<String> getSupportedClaims() {
        return null;
    }

    @Override
    public String getContextPath() {
        return null;
    }
}
