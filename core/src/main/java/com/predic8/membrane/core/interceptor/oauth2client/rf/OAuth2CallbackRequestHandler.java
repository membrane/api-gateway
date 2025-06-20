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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2client.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.math.*;
import java.security.*;

import static com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters.createFrom;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager.*;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.*;

public class OAuth2CallbackRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2CallbackRequestHandler.class);
    public static final String MEMBRANE_MISSING_SESSION = "Missing session.";
    public static final String MEMBRANE_CSRF_TOKEN_MISSING_IN_SESSION = "CSRF token missing in session.";
    public static final String MEMBRANE_CSRF_TOKEN_MISMATCH = "CSRF token mismatch.";
    public static final String MEMBRANE_EXCHANGE_NOT_FOUND = "Exchange to reconstruct not found. Might be a replay of the login.";

    private URIFactory uriFactory;
    private AuthorizationService auth;
    private OriginalExchangeStore originalExchangeStore;
    private AccessTokenRevalidator accessTokenRevalidator;
    private SessionAuthorizer sessionAuthorizer;
    private PublicUrlManager publicUrlManager;
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

        if (onlyRefreshToken && !sessionAuthorizer.isSkipUserInfo())
            throw new RuntimeException("If onlyRefreshToken is set, skipUserInfo also has to be set.");
    }

    public void handleRequest(Exchange exc, Session session) throws Exception {
        try {
            OAuth2Parameters params = OAuth2Parameters.parse(uriFactory, exc);
            params.checkCodeOrError();

            StateManager stateFromUri = new StateManager(params.getState());

            verifyCsrfToken(session, stateFromUri);

            AbstractExchangeSnapshot originalRequest;
            try {
                originalRequest = originalExchangeStore.reconstruct(exc, session, stateFromUri);
            } catch (Exception e) {
                log.warn("Could not reconstruct exchange snapshot '{}'", stateFromUri);
                throw new OAuth2Exception(
                        "MEMBRANE_EXCHANGE_NOT_FOUND",
                        MEMBRANE_EXCHANGE_NOT_FOUND,
                        Response.badRequest().body(MEMBRANE_EXCHANGE_NOT_FOUND).build());
            }
            originalExchangeStore.remove(exc, session, stateFromUri);

            if (log.isDebugEnabled()) {
                log.debug("CSRF token match.");
            }

            OAuth2TokenResponseBody tokenResponse = auth.codeTokenRequest(
                    publicUrlManager.getPublicURLAndReregister(exc) + callbackPath, params.getCode(),
                    PKCEVerifier.getVerifier(stateFromUri, session));

            if (tokenResponse.getAccessToken() == null) {
                if (!onlyRefreshToken)
                    throw new RuntimeException("No access_token received.");

                // todo maybe override from requireAuth via exchange property
                sessionAuthorizer.verifyJWT(exc, tokenResponse.getIdToken(), createFrom(tokenResponse), session);
            } else {
                accessTokenRevalidator.getValidTokens().put(tokenResponse.getAccessToken(), true);
                session.setAccessToken(null, tokenResponse.getAccessToken()); // saving for logout
                if (sessionAuthorizer.isSkipUserInfo()) {
                    // assume access token is JWT
                    sessionAuthorizer.verifyJWT(exc, tokenResponse.getAccessToken(), createFrom(tokenResponse), session);
                } else {
                    sessionAuthorizer.retrieveUserInfo(tokenResponse, createFrom(tokenResponse), session);
                }
            }

            continueOriginalExchange(exc, originalRequest, session);

            originalExchangeStore.postProcess(exc);
        } catch (OAuth2Exception e) {
            // TODO: originalExchangeStore.remove(exc, session, state);
            throw e;
        } catch (Exception e) {
            log.error("Could not exchange code for token.", e);
            exc.setResponse(Response.badRequest().body(e.getMessage()).build());
            originalExchangeStore.postProcess(exc);
        }
    }

    private static void continueOriginalExchange(Exchange exc, AbstractExchangeSnapshot originalRequest, Session session) throws Exception {
        if (originalRequest.getRequest().getMethod().equals("GET")) {
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri(), 302).build());
        } else {
            String oa2redirect = new BigInteger(130, new SecureRandom()).toString(32);

            session.put(OAuthUtils.oa2redictKeyNameInSession(oa2redirect), new ObjectMapper().writeValueAsString(originalRequest));

            String delimiter = originalRequest.getOriginalRequestUri().contains("?") ? "&" : "?";
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri() + delimiter + OA2REDIRECT + "=" + oa2redirect, 302).build());
        }
    }
}
