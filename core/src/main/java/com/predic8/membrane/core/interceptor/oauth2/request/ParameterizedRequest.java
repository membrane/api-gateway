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

package com.predic8.membrane.core.interceptor.oauth2.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.ReusableJsonGenerator;
import com.predic8.membrane.core.util.URLParamUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public abstract class ParameterizedRequest {

    protected Exchange exc;
    protected OAuth2AuthorizationServerInterceptor authServer;
    protected Map<String,String> params;
    protected ReusableJsonGenerator jsonGen;

    protected abstract Response checkForMissingParameters() throws Exception;
    protected abstract Response processWithParameters() throws Exception;
    protected abstract Response getResponse() throws Exception;

    public Response validateRequest() throws Exception {
        Response resp;
        resp = checkForMissingParameters();
        if(resp.getClass() != NoResponse.class)
            return resp;
        resp = processWithParameters();
        if(resp.getClass() != NoResponse.class)
            return resp;
        return getResponse();
    }


    public ParameterizedRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        this.authServer = authServer;
        this.exc = exc;
        this.params = getValidParams(exc);
        this.jsonGen = new ReusableJsonGenerator();
    }

    private Map<String, String> getValidParams(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(authServer.getRouter().getUriFactory(), exc);
        removeEmptyParams(params);
        return params;
    }

    protected void removeEmptyParams(Map<String, String> params) {
        ArrayList<String> toRemove = new ArrayList<String>();
        for (String paramName : params.keySet()) {
            if (params.get(paramName).isEmpty())
                toRemove.add(paramName);
        }
        for(String paramName : toRemove)
            params.remove(paramName);
    }

    protected Response createParameterizedFormUrlencodedRedirect(Exchange exc, String state, String url) {
        if (state != null)
            url += "&state=" + state;
        return Response.redirect(url, false).header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded").bodyEmpty().dontCache().build();
    }

    protected Response buildWwwAuthenticateErrorResponse(Response.ResponseBuilder builder, String errorValue) {
        return builder.bodyEmpty().header(Header.WWW_AUTHENTICATE, authServer.getTokenGenerator().getTokenType() + " error=\""+errorValue+"\"").build();
    }

    protected static String extractSessionId(HeaderField sessionHeader) {
        for (String s : sessionHeader.getValue().split(" ")) {
            if (s.startsWith("SESSIONID=")) {
                return s.substring(10);
            }
        }
        throw new RuntimeException("SessionId not found in Session header!");
    }

    protected SessionManager.Session createSession(Exchange exc, String sessionId){
        synchronized (authServer.getSessionManager()) {
            return authServer.getSessionManager().createSession(exc, sessionId);
        }
    }

    protected SessionManager.Session getSession(Exchange exc) {
        SessionManager.Session session;
        synchronized (authServer.getSessionManager()) {
            session = authServer.getSessionManager().getSession(exc.getRequest());
        }
        return session;
    }

    protected void addParams(SessionManager.Session session, Map<String,String> params){
        synchronized (session) {
            session.getUserAttributes().putAll(params);
        }
    }

    protected boolean verifyUserThroughParams(){
        try {
            authServer.getUserDataProvider().verify(params);
            return true;
        }catch (Exception ignored){
            return false;
        }
    }

    protected SessionManager.Session createSessionForAuthorizedUserWithParams() {
        SessionManager.Session session = authServer.getSessionManager().createSession(exc);
        synchronized(session) {
            session.preAuthorize(getUsername(), params);
            session.authorize();
        }
        return session;
    }

    protected SessionManager.Session createSessionForAuthorizedClientWithParams() {
        SessionManager.Session session = authServer.getSessionManager().createSession(exc);
        synchronized(session) {
            session.preAuthorize(getClientId(), params);
            session.authorize();
        }
        return session;
    }

    protected boolean verifyClientThroughParams(){
        try {
            Client client = authServer.getClientList().getClient(getClientId());
            return client.verify(getClientId(),getClientSecret());
        }catch(Exception e){
            return false;
        }
    }

    protected String createTokenForVerifiedUserAndClient(){
        return authServer.getTokenGenerator().getToken(getUsername(), getClientId(), getClientSecret());
    }

    protected String createTokenForVerifiedClient(){
        return authServer.getTokenGenerator().getToken(getClientId(), getClientId(), getClientSecret());
    }

    public String getPrompt() {
        return params.get(ParamNames.PROMPT);
    }

    public String getClientId() {
        return params.get(ParamNames.CLIENT_ID);
    }

    public String getRedirectUri() {
        return params.get(ParamNames.REDIRECT_URI);
    }

    public String getResponseType() {
        return params.get(ParamNames.RESPONSE_TYPE);
    }

    public String getScope() {
        return params.get(ParamNames.SCOPE);
    }

    public String getState() {
        return params.get(ParamNames.STATE);
    }

    public void setScope(String scope){
        params.put(ParamNames.SCOPE,scope);
    }

    public void setScopeInvalid(String invalidScopes){
        params.put(ParamNames.SCOPE_INVALID,invalidScopes);
    }

    public String getCode(){return params.get(ParamNames.CODE);}

    public String getClientSecret(){return params.get(ParamNames.CLIENT_SECRET);}

    public String getClaims(){return params.get(ParamNames.CLAIMS);}

    public String getGrantType(){return params.get(ParamNames.GRANT_TYPE);}

    public String getUsername(){return params.get(ParamNames.USERNAME);}

    public String getPassword(){return params.get(ParamNames.PASSWORD);}

}
