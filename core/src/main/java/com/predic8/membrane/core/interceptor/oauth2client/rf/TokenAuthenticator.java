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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRevalidator;
import com.predic8.membrane.core.interceptor.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

public class TokenAuthenticator {
    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticator.class);
    private SessionAuthorizer sessionAuthorizer;
    private OAuth2Statistics statistics;
    private AccessTokenRevalidator accessTokenRevalidator;
    private AuthorizationService authService;

    public void init(
            SessionAuthorizer sessionAuthorizer,
            OAuth2Statistics statistics,
            AccessTokenRevalidator accessTokenRevalidator,
            AuthorizationService authService
    ) {
        this.sessionAuthorizer = sessionAuthorizer;
        this.statistics = statistics;
        this.accessTokenRevalidator = accessTokenRevalidator;
        this.authService = authService;
    }

    public boolean userInfoIsNullAndShouldRedirect(
            Session session,
            Exchange exc,
            String wantedScope) throws Exception {
        if (!sessionAuthorizer.isSkipUserInfo() && !session.isVerified()) {
            String auth = exc.getRequest().getHeader().getFirstValue(AUTHORIZATION);
            if (auth != null && isBearer(auth)) {
                session.setAccessToken(wantedScope, auth.substring(7));

                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                oauth2Answer.setAccessToken(auth.substring(7));
                oauth2Answer.setTokenType("Bearer");

                Map<String, Object> userinfo =
                        accessTokenRevalidator.revalidate(session, statistics, wantedScope);

                if (logUserInfoIsNull(userinfo)) {
                    return true;
                }

                oauth2Answer.setUserinfo(userinfo);

                session.setOAuth2Answer(wantedScope, oauth2Answer.serialize());
                sessionAuthorizer.authorizeSession(userinfo, session, authService);
            }
        }
        return false;
    }

    private static boolean logUserInfoIsNull(Map<String, Object> userinfo) {
        if (userinfo == null) {
            log.debug("userinfo is null, redirecting.");
            return true;
        }
        return false;
    }

    private static boolean isBearer(String auth) {
        return auth.substring(0, 7).equalsIgnoreCase("Bearer ");
    }
}
