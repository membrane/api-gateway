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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2client.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.*;

import java.math.*;
import java.security.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager.*;
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
            Map<String, String> params = getparamsFromRequest(exc);

            String stateFromUri = getSecurityTokenFromState(params.get("state"));

            if (!csrfTokenMatches(session, stateFromUri)) {
                throw new RuntimeException("CSRF token mismatch.");
            }

            // state in session can be "merged" -> save the selected state in session overwriting the possibly merged value
            session.put(ParamNames.STATE, stateFromUri);

            originalExchangeStore.remove(exc, session, stateFromUri);

            // TODO LogHelper heiﬂt des oder so
            if (log.isDebugEnabled()) {
                log.debug("CSRF token match.");
            }

            String tokenEndpoint = auth.getTokenEndpoint();
            if (session.get("defaultFlow") != null) {
                tokenEndpoint = tokenEndpoint.replaceAll(session.get("defaultFlow"), session.get("triggerFlow"));
            }

            verifyToken(exc, session, exchangeCodeForToken(tokenEndpoint, publicUrlManager.getPublicURL(exc), params));

            doRedirect(exc, originalExchangeStore.reconstruct(exc, session, stateFromUri), session);

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

    private @NotNull Map<String, String> getparamsFromRequest(Exchange exc) throws Exception {
        return URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
    }

    private void verifyToken(Exchange exc, Session session, Map<String, Object> json) throws Exception {
        if (!json.containsKey("access_token")) {
            handleRefreshTokenOnly(exc, session, json);
            return;
        }

        String token = (String) json.get("access_token");
        if (token == null) {
            throw new RuntimeException("OAuth2 response with access_token set to null.");
        }

        handleAccessToken(exc, session, json, token);
    }

    private void handleRefreshTokenOnly(Exchange exc, Session session, Map<String, Object> json) throws Exception {
        if (!onlyRefreshToken) {
            throw new RuntimeException("No access_token received.");
        }

        OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
        tokenResponseHandler.handleTokenResponse(session, null, json, oauth2Answer);
        sessionAuthorizer.verifyJWT(exc, (String) json.get("id_token"), oauth2Answer, session);
    }

    private void handleAccessToken(Exchange exc, Session session, Map<String, Object> json, String token) throws Exception {
        accessTokenRevalidator.getValidTokens().put(token, true);

        OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
        tokenResponseHandler.handleTokenResponse(session, null, json, oauth2Answer);

        if (sessionAuthorizer.isSkipUserInfo()) {
            sessionAuthorizer.verifyJWT(exc, token, oauth2Answer, session);
            return;
        }

        sessionAuthorizer.retrieveUserInfo(
                json.get("token_type").toString(),
                token,
                oauth2Answer,
                session
        );
    }

    private Map<String, Object> exchangeCodeForToken(String tokenEndpoint, String publicUrl, Map<String, String> params) throws Exception {
        Exchange e = auth.applyAuth(new Request.Builder()
                                .post(tokenEndpoint)
                                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                                .header(ACCEPT, APPLICATION_JSON)
                                .header(USER_AGENT, USERAGENT),
                        "code=" + getCode(params)
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

        return new ObjectMapper().readValue(response.getBodyAsStreamDecoded(), new TypeReference<>() {
        });
    }

    private static @NotNull String getCode(Map<String, String> params) throws OAuth2Exception {
        String code = params.get("code");
        if(code != null)
            return code;

        String error = params.get("error");

        if(error == null)
            throw new RuntimeException("No code received.");

        ProblemDetails pd = ProblemDetails.security(false)
                .statusCode(500)
                .addSubType("oauth2-error-from-authentication-server")
                .title("OAuth2 Error from Authentication Server");
        pd.detail(params.get("error_description"));
        pd.extension("error", error);
        throw new OAuth2Exception(error, params.get("error_description"), pd.build());
    }

    private static void doRedirect(Exchange exc, AbstractExchangeSnapshot originalRequest, Session session) throws JsonProcessingException {
        if (originalRequest.getRequest().getMethod().equals("GET")) {
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri(), false).build());
        } else {
            session.put(OAuthUtils.oa2redictKeyNameInSession(
                            new BigInteger(130, new SecureRandom()).toString(32)),
                    new ObjectMapper().writeValueAsString(originalRequest)
            );
            exc.setResponse(Response.redirect(
                    originalRequest.getOriginalRequestUri()
                            + (originalRequest.getOriginalRequestUri().contains("?") ? "&" : "?")
                            + OA2REDIRECT + "="
                            + new BigInteger(130, new SecureRandom()).toString(32), false).build()
            );
        }
    }
}
