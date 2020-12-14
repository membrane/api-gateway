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

package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.security.oauth2openid.Constants;
import com.bornium.security.oauth2openid.server.AuthorizationServer;
import com.bornium.security.oauth2openid.token.IdTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.AccountBlocker;
import com.predic8.membrane.core.interceptor.authentication.session.CleanupThread;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerTokenGenerator;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.TokenGenerator;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@MCElement(name = "oauth2authserver2")
public class OAuth2AuthorizationServer2Interceptor extends AbstractInterceptorWithSession {
    private static Logger log = LoggerFactory.getLogger(OAuth2AuthorizationServer2Interceptor.class.getName());

    AuthorizationServer authorizationServer;

    private String issuer;
    private String location;
    private String basePath;
    private String path = "/login/";
    private String message;
    private String consentFile;
    private boolean exposeUserCredentialsToSession;
    private boolean loginViewDisabled = false;
    private boolean issueNonSpecIdTokens = false;
    private boolean issueNonSpecRefreshTokens = false;

    private Router router;
    private UserDataProvider userDataProvider;
    private AccountBlocker accountBlocker;
    private ClientList clientList;
    private TokenGenerator tokenGenerator = new BearerTokenGenerator();


    private TokenGenerator refreshTokenGenerator = new BearerTokenGenerator();
    private ClaimList claimList;
    private OAuth2Statistics statistics;

    private SessionFinder sessionFinder = new SessionFinder();
    private ConsentPageFile consentPageFile = new ConsentPageFile();

    private String subClaimName = "username";
    private String loginPath = "/login/";
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init(Router router) throws Exception {
        name = "OAuth 2 Authorization Server";
        setFlow(Flow.Set.REQUEST_RESPONSE);

        this.setRouter(router);
        basePath = computeBasePath();
        if (basePath.endsWith("/"))
            throw new RuntimeException("When <oauth2AuthorizationServer> is nested in a <serviceProxy> with a <path>, the path should not end in a '/'.");

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

        getClaimList().init(router);
        setSessionManager(new JwtSessionManager());
        getSessionManager().init(router);
        statistics = new OAuth2Statistics();

        integrateAuthorizationServer();

        new CleanupThread(accountBlocker).start();
    }

    private void integrateAuthorizationServer() throws Exception {
        clientList.init(router);
        userDataProvider.init(router);
        authorizationServer = new AuthorizationServer(new MembraneProvidedServices(
                getSessionManager(),
                getClientList(),
                getUserDataProvider(),
                subClaimName,
                getIssuer(),
                getClaimList().getSupportedClaims(),
                this.getBasePath()), new IdTokenProvider(), serverServices -> {
            try {
                if(loginViewDisabled)
                    return Arrays.asList();

                return Arrays.asList(new LoginEndpoint(router, serverServices, getUserDataProvider(), getSessionManager(), getLocation(), loginPath, getConsentPageFile(),"/login/login", "/login/consent"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        consentPageFile.init(router, getConsentFile());
    }

    @Override
    public Outcome handleRequestInternal(Exchange exc) throws Exception {
        Outcome preprocessOutcome = preprocess(exc);
        if(preprocessOutcome != Outcome.CONTINUE)
            return preprocessOutcome;

        com.bornium.http.Exchange internalExc = authorizationServer.invokeOn(Convert.convertFromMembraneExchange(exc));
        exc.setResponse(Convert.convertToMembraneResponse(internalExc.getResponse()));
        exc.getProperties().putAll(internalExc.getProperties());


        return postProcess(exc);
    }

    private Outcome postProcess(Exchange exc) throws Exception {
        if(loginViewDisabled) {
            if (exc.getRequestURI().contains(Constants.ENDPOINT_WELL_KNOWN))
                removeUnsupportedFlowsFromWellKnown(exc);
        }

        return Outcome.RETURN;
    }

    private Outcome preprocess(Exchange exc) throws Exception {
        if(loginViewDisabled){
            if(exc.getRequestURI().contains(Constants.ENDPOINT_AUTHORIZATION + "?"))
                return disableUnsupportedFlows(exc);
        }

        return Outcome.CONTINUE;
    }

    private void removeUnsupportedFlowsFromWellKnown(Exchange exc) throws Exception {
        Map<String, Object> params = objectMapper.readValue(exc.getResponse().getBodyAsStream(),Map.class);
        params.remove("response_types_supported");
        List<String> grantTypesSupported = (List<String>)params.get("grant_types_supported");
        grantTypesSupported.remove("authorization_code");
        grantTypesSupported.remove("implicit");
        exc.getResponse().setBodyContent(objectMapper.writeValueAsBytes(params));
    }

    private Outcome disableUnsupportedFlows(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(getRouter().getUriFactory(), exc);
        String responseType = params.get(Constants.PARAMETER_RESPONSE_TYPE);

        if(responseType != null && (responseType.equals(Constants.PARAMETER_VALUE_CODE) || responseType.equals(Constants.PARAMETER_VALUE_TOKEN))){
            exc.setResponse(Response.notImplemented().build());
            return Outcome.RETURN;
        }

        return Outcome.CONTINUE;
    }

    @Override
    protected Outcome handleResponseInternal(Exchange exc) throws Exception {
        return Outcome.CONTINUE;
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    @Required
    @MCChildElement(order = 1)
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
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

    public SessionFinder getSessionFinder() {
        return sessionFinder;
    }

    public void setSessionFinder(SessionFinder sessionFinder) {
        this.sessionFinder = sessionFinder;
    }

    public String getIssuer() {
        return issuer;
    }

    @Required
    @MCAttribute
    public void setIssuer(String issuer) {
        this.issuer = issuer;
        if (issuer.endsWith("/"))
            log.warn("In <oauth2authserver>, the 'issuer' attribute ends with a '/'. This should be avoided.");
    }

    public ClaimList getClaimList() {
        return claimList;
    }

    @Required
    @MCChildElement(order = 6)
    public void setClaimList(ClaimList claimList) {
        this.claimList = claimList;
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

    public String computeBasePath() {
        Rule rule = getRule();
        if (rule == null)
            return "";
        if (rule.getKey().getPath() == null || rule.getKey().isPathRegExp())
            return "";
        return rule.getKey().getPath();
    }

    public String getBasePath() {
        return basePath;
    }
}
