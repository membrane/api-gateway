/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.AccountBlocker;
import com.predic8.membrane.core.interceptor.authentication.session.CleanupThread;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.interceptor.oauth2.processors.*;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerTokenGenerator;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.HashSet;

@MCElement(name = "oauth2authserver")
public class OAuth2AuthorizationServerInterceptor extends AbstractInterceptor {
    private static Logger log = LoggerFactory.getLogger(OAuth2AuthorizationServerInterceptor.class.getName());

    private String issuer;
    private String location;
    private String path = "/login/";
    private String message;
    private String consentFile;
    private boolean exposeUserCredentialsToSession;
    private boolean loginViewDisabled = false;
    private boolean issueNonSpecIdTokens = false;
    private boolean issueNonSpecRefreshTokens = false;

    private Router router;
    private UserDataProvider userDataProvider;
    private SessionManager sessionManager = new SessionManager();
    private AccountBlocker accountBlocker;
    private ClientList clientList;
    private TokenGenerator tokenGenerator = new BearerTokenGenerator();


    private TokenGenerator refreshTokenGenerator = new BearerTokenGenerator();
    private ClaimList claimList;
    private OAuth2Statistics statistics;

    private JwtGenerator jwtGenerator;
    private OAuth2Processors processors = new OAuth2Processors();
    private HashSet<String> supportedAuthorizationGrants = new HashSet<String>();
    private SessionFinder sessionFinder = new SessionFinder();
    private WellknownFile wellknownFile = new WellknownFile();
    private ConsentPageFile consentPageFile = new ConsentPageFile();

    @Override
    public void init(Router router) throws Exception {
        name = "OAuth 2 Authorization Server";
        setFlow(Flow.Set.REQUEST_RESPONSE);

        this.setRouter(router);
        addSupportedAuthorizationGrants();
        getWellknownFile().init(router,this);
        getConsentPageFile().init(router,getConsentFile());
        if (userDataProvider == null)
            throw new Exception("No userDataProvider configured. - Cannot work without one.");
        if (getClientList() == null)
            throw new Exception("No clientList configured. - Cannot work without one.");
        if (getClaimList() == null)
            throw new Exception("No scopeList configured. - Cannot work without one");
        if(getLocation() == null) {
            log.warn("===========================================================================================");
            log.warn("IMPORTANT: No location configured - Authorization code and implicit flows are not available");
            log.warn("===========================================================================================");
            loginViewDisabled = true;
        }
        if(getConsentFile() == null && !isLoginViewDisabled()){
            log.warn("==============================================================================================");
            log.warn("IMPORTANT: No consentFile configured - Authorization code and implicit flows are not available");
            log.warn("==============================================================================================");
            loginViewDisabled = true;
        }
        if(getPath() == null)
            throw new Exception("No path configured. - Cannot work without one");
        userDataProvider.init(router);
        getClientList().init(router);
        getClaimList().init(router);
        jwtGenerator = new JwtGenerator();
        sessionManager.init(router);
        statistics = new OAuth2Statistics();
        addDefaultProcessors();
        new CleanupThread(sessionManager, accountBlocker).start();
    }

    private void addDefaultProcessors() {
        getProcessors()
                .add(new InvalidMethodProcessor(this))
                .add(new FaviconEndpointProcessor(this))
                .add(new AuthEndpointProcessor(this))
                .add(new TokenEndpointProcessor(this))
                .add(new UserinfoEndpointProcessor(this))
                .add(new RevocationEndpointProcessor(this))
                .add(new LoginDialogEndpointProcessor(this))
                .add(new WellknownEndpointProcessor(this))
                .add(new CertsEndpointProcessor(this))
                .add(new EmptyEndpointProcessor(this))
                .add(new DefaultEndpointProcessor(this));
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        Outcome outcome = getProcessors().runProcessors(exc);
        if (outcome != Outcome.CONTINUE)
            sessionManager.postProcess(exc);
        return outcome;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        sessionManager.postProcess(exc);
        return super.handleResponse(exc);
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    @Required
    @MCChildElement(order = 1)
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @MCChildElement(order = 2)
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String getLocation() {
        return location;
    }

    /**
     * @description Base path under which the login dialog will be served.
     * @example logindialog
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getPath() {
        return path;
    }

    @MCAttribute
    public void setPath(String path) {
        this.path = path;
    }

    public String getMessage() {
        return message;
    }

    @MCAttribute
    public void setMessage(String message) {
        this.message = message;
    }

    public AccountBlocker getAccountBlocker() {
        return accountBlocker;
    }

    @MCChildElement(order = 3)
    public void setAccountBlocker(AccountBlocker accountBlocker) {
        this.accountBlocker = accountBlocker;
    }

    public boolean isExposeUserCredentialsToSession() {
        return exposeUserCredentialsToSession;
    }

    @MCAttribute
    public void setExposeUserCredentialsToSession(boolean exposeUserCredentialsToSession) {
        this.exposeUserCredentialsToSession = exposeUserCredentialsToSession;
    }

    public ClientList getClientList() {
        return clientList;
    }

    @Required
    @MCChildElement(order = 4)
    public void setClientList(ClientList clientList) {
        this.clientList = clientList;
    }

    public TokenGenerator getTokenGenerator() {
        return tokenGenerator;
    }

    @MCChildElement(order = 5)
    public void setTokenGenerator(TokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Router getRouter() {
        return router;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public HashSet<String> getSupportedAuthorizationGrants() {
        return supportedAuthorizationGrants;
    }

    public void setSupportedAuthorizationGrants(HashSet<String> supportedAuthorizationGrants) {
        this.supportedAuthorizationGrants = supportedAuthorizationGrants;
    }

    public OAuth2Processors getProcessors() {
        return processors;
    }

    public void setProcessors(OAuth2Processors processors) {
        this.processors = processors;
    }

    public SessionFinder getSessionFinder() {
        return sessionFinder;
    }

    public void setSessionFinder(SessionFinder sessionFinder) {
        this.sessionFinder = sessionFinder;
    }

    public JwtGenerator getJwtGenerator() {
        return jwtGenerator;
    }

    public String getIssuer() {
        return issuer;
    }

    @Required
    @MCAttribute
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public ClaimList getClaimList() {
        return claimList;
    }

    @Required
    @MCChildElement(order = 6)
    public void setClaimList(ClaimList claimList) {
        this.claimList = claimList;
    }


    public WellknownFile getWellknownFile() {
        return wellknownFile;
    }

    public void setWellknownFile(WellknownFile wellknownFile) {
        this.wellknownFile = wellknownFile;
    }

    public String getConsentFile() {
        return consentFile;
    }

    @MCAttribute
    public void setConsentFile(String consentFile) {
        this.consentFile = consentFile;
    }

    public ConsentPageFile getConsentPageFile() {
        return consentPageFile;
    }

    public void setConsentPageFile(ConsentPageFile consentPageFile) {
        this.consentPageFile = consentPageFile;
    }

    @Override
    public String getShortDescription() {
        return "Authorization server of the oauth2 authentication process.\n" + statistics.toString();
    }

    public void addSupportedAuthorizationGrants() {
        getSupportedAuthorizationGrants().add("code");
        getSupportedAuthorizationGrants().add("token");
        getSupportedAuthorizationGrants().add("id_token token");
    }

    public OAuth2Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(OAuth2Statistics statistics) {
        this.statistics = statistics;
    }


    public TokenGenerator getRefreshTokenGenerator() {
        return refreshTokenGenerator;
    }

    public void setRefreshTokenGenerator(TokenGenerator refreshTokenGenerator) {
        this.refreshTokenGenerator = refreshTokenGenerator;
    }

    public boolean isLoginViewDisabled() {
        return loginViewDisabled;
    }

    public void setLoginViewDisabled(boolean loginViewDisabled) {
        this.loginViewDisabled = loginViewDisabled;
    }

    public boolean isIssueNonSpecIdTokens() {
        return issueNonSpecIdTokens;
    }

    /**
     * @description Issue id-tokens also in credentials-flow and password-flow . The OIDC specification, which brings in id-tokens, does not handle those flows, which is why the default value is false.
     * @default false
     */
    @MCAttribute
    public void setIssueNonSpecIdTokens(boolean issueNonSpecIdTokens) {
        this.issueNonSpecIdTokens = issueNonSpecIdTokens;
    }

    public boolean isIssueNonSpecRefreshTokens() {
        return issueNonSpecRefreshTokens;
    }

    /**
     * @description Issue refresh-tokens also in credentials-flow. The OAuth2 specification does not issue refresh tokens in the credentials-flow, which is why the default value is false.
     * @default false
     */
    @MCAttribute
    public void setIssueNonSpecRefreshTokens(boolean issueNonSpecRefreshTokens) {
        this.issueNonSpecRefreshTokens = issueNonSpecRefreshTokens;
    }
}
