/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.OriginalExchangeStore;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRevalidator;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.TokenResponseHandler;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.exceptions.ProblemDetails.createProblemDetails;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.interceptor.oauth2.ParamNames.ACCESS_TOKEN;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager.csrfTokenMatches;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager.getSecurityTokenFromState;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.*;

public class OAuth2CallbackRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2CallbackRequestHandler.class);

    LogHelper logHelper = new LogHelper();
    private URIFactory uriFactory;
    private AuthorizationService auth;
    private OriginalExchangeStore originalExchangeStore;
    private AccessTokenRevalidator accessTokenRevalidator;
    private SessionAuthorizer sessionAuthorizer;
    private PublicUrlManager publicUrlManager;
    private TokenResponseHandler tokenResponseHandler;
    private String callbackPath;
    private boolean onlyRefreshToken;

    public void init(
            URIFactory uriFactory,
            AuthorizationService auth,
            OriginalExchangeStore originalExchangeStore,
            AccessTokenRevalidator accessTokenRevalidator,
            SessionAuthorizer sessionAuthorizer,
            PublicUrlManager publicUrlManager,
            String callbackPath,
            boolean onlyRefreshToken
            ) {
        this.uriFactory = uriFactory;
        this.auth = auth;
        this.originalExchangeStore = originalExchangeStore;
        this.accessTokenRevalidator = accessTokenRevalidator;
        this.sessionAuthorizer = sessionAuthorizer;
        this.publicUrlManager = publicUrlManager;
        this.callbackPath = callbackPath;
        this.onlyRefreshToken = onlyRefreshToken;
        tokenResponseHandler = new TokenResponseHandler();
        tokenResponseHandler.init(auth);

        if (onlyRefreshToken && !sessionAuthorizer.isSkipUserInfo())
            throw new RuntimeException("If onlyRefreshToken is set, skipUserInfo also has to be set.");
    }

    public boolean handleRequest(Exchange exc, Session session) throws Exception {
        try {
            Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

            String state2 = params.get("state");

            String stateFromUri = getSecurityTokenFromState(state2);

            if (!csrfTokenMatches(session, stateFromUri)) {
                throw new RuntimeException("CSRF token mismatch.");
            }

            // state in session can be "merged" -> save the selected state in session overwriting the possibly merged value
            session.put(ParamNames.STATE, stateFromUri);

            AbstractExchangeSnapshot originalRequest = originalExchangeStore.reconstruct(exc, session, stateFromUri);
            String url = originalRequest.getRequest().getUri();
            if (url == null) {
                url = "/";
            }
            originalExchangeStore.remove(exc, session, stateFromUri);

            if (log.isDebugEnabled()) {
                log.debug("CSRF token match.");
            }

            String tokenEndpoint = auth.getTokenEndpoint();
            if (session.get("defaultFlow") != null) {
                tokenEndpoint = tokenEndpoint.replaceAll(session.get("defaultFlow"), session.get("triggerFlow"));
                session.remove("defaultFlow", "triggerFlow");
            }
            Map<String, Object> json = exchangeCodeForToken(
                    tokenEndpoint, publicUrlManager.getPublicURL(exc), params);

            String token = null;
            if (!json.containsKey("access_token")) {
                if (!onlyRefreshToken)
                    throw new RuntimeException("No access_token received.");
                // todo maybe override from requireAuth via exchange property
                String idToken = (String) json.get("id_token");
                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                tokenResponseHandler.handleTokenResponse(json, oauth2Answer);
                sessionAuthorizer.verifyJWT(exc, idToken, oauth2Answer, session);
            } else {
                token = (String) json.get("access_token"); // and also "scope": "", "token_type": "bearer"
                if (token == null)
                    throw new RuntimeException("OAuth2 response with access_token set to null.");
                session.put(ACCESS_TOKEN, token); // saving for logout
                accessTokenRevalidator.getValidTokens().put(token, true);
                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                tokenResponseHandler.handleTokenResponse(json, oauth2Answer);
                if (!sessionAuthorizer.isSkipUserInfo()) {
                    sessionAuthorizer.retrieveUserInfo(json.get("token_type").toString(), token, oauth2Answer, session);
                } else {
                    // assume access token is JWT
                    sessionAuthorizer.verifyJWT(exc, token, oauth2Answer, session);
                }
            }

            doRedirect(exc, originalRequest, session);

            originalExchangeStore.postProcess(exc);
            return true;
        } catch (OAuth2Exception e) {
            // TODO: originalExchangeStore.remove(exc, session, state);
            throw e;
        } catch (Exception e) {
            log.error("could not exchange code for token", e);
            exc.setResponse(Response.badRequest().body(e.getMessage()).build());
            originalExchangeStore.postProcess(exc);
            return true;
        }
    }

    private Map<String, Object> exchangeCodeForToken(String tokenEndpoint, String publicUrl, Map<String, String> params) throws Exception {

        String code = params.get("code");
        if (code == null) {
            String error = params.get("error");
            if (error != null) {
                String errorDescription = params.get("error_description");

                Map<String, Object> details = new HashMap<>();
                details.put("error",  error);
                if (errorDescription != null)
                    details.put("error_description", errorDescription);

                log.info("Error from Authorization Server: error="+error + (errorDescription != null ? " error_description=" + errorDescription.replaceAll("[\r\n]", " ") : ""));
                throw new OAuth2Exception(error, errorDescription,
                    createProblemDetails(500, "/oauth2-error-from-authentication-server",
                        "OAuth2 Error from Authentication Server", details));
            }

            throw new RuntimeException("No code received.");
        }

        Exchange e = auth.applyAuth(new Request.Builder()
                                .post(tokenEndpoint)
                                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                                .header(ACCEPT, APPLICATION_JSON)
                                .header(USER_AGENT, USERAGENT),
                        "code=" + code
                                + "&redirect_uri=" + publicUrl
                                + callbackPath + "&grant_type=authorization_code")
                .buildExchange();

        logHelper.handleRequest(e);

        Response response = auth.doRequest(e);

        logHelper.handleResponse(e);

        if (response.getStatusCode() != 200) {
            response.getBody().read();
            throw new RuntimeException("Authorization server returned " + response.getStatusCode() + ".");
        }

        if (!isJson(response)) {
            throw new RuntimeException("Token response is no JSON.");
        }

        return new ObjectMapper().readValue(response.getBodyAsStreamDecoded(), new TypeReference<>(){});
    }

    private static void doRedirect(Exchange exc, AbstractExchangeSnapshot originalRequest, Session session) throws JsonProcessingException {
        if (originalRequest.getRequest().getMethod().equals("GET")) {
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri(), false).build());
        } else {
            String oa2redirect = new BigInteger(130, new SecureRandom()).toString(32);

            session.put(OAuthUtils.oa2redictKeyNameInSession(oa2redirect), new ObjectMapper().writeValueAsString(originalRequest));

            String delimiter = originalRequest.getOriginalRequestUri().contains("?") ? "&" : "?";
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri() + delimiter + OA2REDIRECT + "=" + oa2redirect, false).build());
        }
    }
}
