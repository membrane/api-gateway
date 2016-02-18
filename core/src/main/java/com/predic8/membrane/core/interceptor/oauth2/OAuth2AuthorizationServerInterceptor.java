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

import com.github.fge.jsonschema.core.util.URIUtils;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.URLUtil;
import org.springframework.beans.factory.annotation.Required;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

@MCElement(name="oauth2authserver")
public class OAuth2AuthorizationServerInterceptor extends AbstractInterceptor {

    public static final String AUTHORIZATION_CODE = "AuthorizationCode";

    private String location;
    private String path;
    private String message;
    private boolean exposeUserCredentialsToSession;

    private UserDataProvider userDataProvider;
    private LoginDialog loginDialog;
    private SessionManager sessionManager;
    private AccountBlocker accountBlocker;
    private ClientList clientList;

    private SecureRandom random = new SecureRandom();
    private URIFactory uriFactory;

    private HashMap<String,Session> authCodesToSession = new HashMap<String, Session>();


    @Override
    public void init(Router router) throws Exception {
        uriFactory = router.getUriFactory();
        if (userDataProvider == null)
            throw new Exception("No userDataProvider configured. - Cannot work without one.");
        if (getClientList() == null)
            throw new Exception("No clientList configured. - Cannot work without one.");
        if (sessionManager == null)
            sessionManager = new SessionManager();
        userDataProvider.init(router);
        getClientList().init(router);
        loginDialog = new LoginDialog(getUserDataProvider(), null, getSessionManager(), getAccountBlocker(), getLocation(), getPath(), isExposeUserCredentialsToSession(), getMessage());
        loginDialog.init(router);
        sessionManager.init(router);
        new CleanupThread(sessionManager, accountBlocker).start();
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        SessionManager.Session s = sessionManager.getSession(exc.getRequest());
        if(isOAuth2AuthCall(exc)) {
            Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);
            exc.setResponse(new Response.ResponseBuilder().build());
            String givenSessionId = extractSessionId(extraxtSessionHeader(exc.getRequest()));
            s = sessionManager.createSession(exc, givenSessionId);
            s.getUserAttributes().putAll(params);
            return redirectToLoginWithSession(exc, extraxtSessionHeader(exc.getResponse()));
        }
        else if(isOAuth2TokenCall(exc)){
            System.out.println("TOKEN");



            // TODO do something here

            return Outcome.CONTINUE;
        }
        else if (loginDialog.isLoginRequest(exc)) {
            loginDialog.handleLoginRequest(exc);
            extractSessionFromRequestAndAddToResponse(exc);

            return Outcome.RETURN;
        }
        else if (exc.getRequestURI().equals("/") && s != null && s.isPreAuthorized()){
            s.authorize();
            Client client = clientList.getClient(s.getUserAttributes().get("client_id"));
            String savedCallbackUrl = client.getCallbackUrl();
            if(savedCallbackUrl.equals(s.getUserAttributes().get("redirect_uri"))) {
                String code = generateAuthorizationCode(s);
                doBackendAuthorization(code,s);
                return respondWithAuthorizationCodeAndRedirect(exc, code);
            }
            else {
                exc.setResponse(Response.badRequest().build());
                return Outcome.RETURN;
            }
        }
        else{
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }
    }

    private boolean isOAuth2TokenCall(Exchange exc) {
        return exc.getRequestURI().startsWith("/oauth2/token");
    }

    private void doBackendAuthorization(String code, Session s) {
        s.getUserAttributes().put(AUTHORIZATION_CODE,code);
        //TODO respect scope

        authCodesToSession.put(code,s);
    }

    private boolean isOAuth2AuthCall(Exchange exc) {
        return exc.getRequestURI().startsWith("/oauth2/auth");
    }

    private Outcome respondWithAuthorizationCodeAndRedirect(Exchange exc, String code) throws UnsupportedEncodingException {
        Session s = sessionManager.getSession(exc.getRequest());
        String state = s.getUserAttributes().get("state");

        exc.setResponse(Response.
                redirect(s.getUserAttributes().get("redirect_uri")+"?code="+code + (state == null ? "" : "&state="+state), false).
                dontCache().
                body("").
                build());
        extractSessionFromRequestAndAddToResponse(exc);
        return Outcome.RETURN;
    }

    private String generateAuthorizationCode(Session s) {
        return new BigInteger(130, random).toString(32);
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    public Outcome redirectToLoginWithSession(Exchange exc, HeaderField session) throws MalformedURLException, UnsupportedEncodingException {
        exc.setResponse(Response.
                redirect(path, false).
                dontCache().
                body("").
                build());
        addSessionHeader(exc.getResponse(),session);
        return Outcome.RETURN;
    }

    public HeaderField extraxtSessionHeader(Message msg){
        for(HeaderField h : msg.getHeader().getAllHeaderFields()) {
            if (h.getHeaderName().equals("Set-Cookie")) {
                return h;
            }
            else if(h.getHeaderName().equals("Cookie")) {
                h.setHeaderName(new HeaderName("Set-Cookie"));
                return h;
            }
        }
        throw new RuntimeException();
    }

    public String extractSessionId(HeaderField sessionHeader){
        String[] splitted = sessionHeader.getValue().split(" ");
        for(String s : splitted){
            if(s.startsWith("SESSIONID=")){
                return s.substring(10);
            }
        }
        throw new RuntimeException();
    }

    public Message addSessionHeader(Message msg, HeaderField session){
        msg.getHeader().add(session);
        return msg;
    }

    public void extractSessionFromRequestAndAddToResponse(Exchange exc){
        addSessionHeader(exc.getResponse(),extraxtSessionHeader(exc.getRequest()));
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

    @Required
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getPath() {
        return path;
    }

    @Required
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
}
