package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.security.oauth2openid.server.AuthorizationServer;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name = "oauth2authserver2")
public class OAuth2AuthorizationServer2Interceptor extends AbstractInterceptor {

    AuthorizationServer authorizationServer;

    public OAuth2AuthorizationServer2Interceptor() throws Exception {
        authorizationServer = new AuthorizationServer(new MembraneProvidedServices());


    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return Outcome.CONTINUE;
    }
}
