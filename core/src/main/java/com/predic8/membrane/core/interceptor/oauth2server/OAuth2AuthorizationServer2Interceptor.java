package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.security.oauth2openid.server.AuthorizationServer;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.interceptor.oauth2.ClientList;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@MCElement(name = "oauth2authserver2")
public class OAuth2AuthorizationServer2Interceptor extends AbstractInterceptorWithSession {

    AuthorizationServer authorizationServer;
    ClientList clientList;
    private UserDataProvider userDataProvider;
    private String subClaimName;
    private String issuer;
    private Set<String> supportedClaims;
    private String contextPath;

    @Override
    public void init() throws Exception {
        super.init();
        authorizationServer = new AuthorizationServer(new MembraneProvidedServices(
                getSessionManager(),
                clientList,
                userDataProvider,
                subClaimName,
                issuer,
                supportedClaims,
                contextPath));
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        com.bornium.http.Exchange internalExc = authorizationServer.invokeOn(Convert.convertFromMembraneExchange(exc));
        exc.setResponse(Convert.convertToMembraneResponse(internalExc.getResponse()));
        return Outcome.RETURN;
    }

    @Override
    protected Outcome handleResponseInternal(Exchange exc) throws Exception {
        return Outcome.CONTINUE;
    }

    public ClientList getClientList() {
        return clientList;
    }

    @MCChildElement(order = 10)
    public void setClientList(ClientList clientList) {
        this.clientList = clientList;
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    @MCChildElement(order = 20)
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
    }

    public String getSubClaimName() {
        return subClaimName;
    }

    @MCAttribute
    public void setSubClaimName(String subClaimName) {
        this.subClaimName = subClaimName;
    }

    public String getIssuer() {
        return issuer;
    }

    @MCAttribute
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<Claim> getSupportedClaims() {
        return supportedClaims.stream().map(claim -> new Claim(claim)).collect(Collectors.toList());
    }

    @MCChildElement(order = 30)
    public void setSupportedClaims(List<Claim> supportedClaims) {
        this.supportedClaims = supportedClaims
                .stream()
                .map(claim -> claim.getClaimName())
                .collect(Collectors.toSet());
    }

    public String getContextPath() {
        return contextPath;
    }

    @MCAttribute
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }


}
