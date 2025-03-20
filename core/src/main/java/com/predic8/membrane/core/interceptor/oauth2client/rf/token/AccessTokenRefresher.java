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
package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.exchange.Exchange.OAUTH2;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor.WANTED_SCOPE;

public class AccessTokenRefresher {
    private static final Logger log = LoggerFactory.getLogger(AccessTokenRefresher.class);

    private final Cache<String, Object> synchronizers = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    private AuthorizationService auth;
    private boolean onlyRefreshToken;

    public void init(AuthorizationService auth, boolean onlyRefreshToken) {
        this.auth = auth;
        this.onlyRefreshToken = onlyRefreshToken;
    }

    public void refreshIfNeeded(Session session, Exchange exc) {
        String wantedScope = exc.getPropertyOrNull(WANTED_SCOPE, String.class);
        if (!refreshingOfAccessTokenIsNeeded(session, wantedScope)) {
            return;
        }

        synchronized (getTokenSynchronizer(session)) {
            try {
                exc.setProperty(OAUTH2, refreshAccessToken(session, wantedScope));
            } catch (Exception e) {
                log.warn("Failed to refresh access token, clearing session and restarting OAuth2 flow.", e);
                session.clearAuthentication();
            }
        }
    }

    private OAuth2AnswerParameters refreshAccessToken(Session session, String wantedScope) throws Exception {
        var params = session.getOAuth2AnswerParameters();
        var tokenResponse = auth.refreshTokenRequest(session, wantedScope, params.getRefreshToken());

        if (!onlyRefreshToken && tokenResponse.isMissingOneToken()) {
            throw new RuntimeException("Statuscode was ok but no access_token and refresh_token was received: " + tokenResponse);
        }

        // TODO: OAuth2CallbackRequestHandler does more stuff before calling handleTokenResponse()
        if (tokenResponse.getAccessToken() != null)
            session.setAccessToken(wantedScope, tokenResponse.getAccessToken()); // saving for logout
        params.readFrom(tokenResponse);

        session.setOAuth2Answer(wantedScope, params.serialize());

        return params;
    }

    private boolean refreshingOfAccessTokenIsNeeded(Session session, String wantedScope) {
        if (session.getOAuth2Answer(wantedScope) == null) {
            return wantedScope != null && session.getOAuth2Answer() != null;
        }

        if (session.getAccessToken(wantedScope) == null && wantedScope != null)
            return true;

        var params = session.getOAuth2AnswerParameters(wantedScope);
        var expiration = params.getExpiration();

        if (isNullOrEmpty(session.getOAuth2AnswerParameters().getRefreshToken(), expiration)) {
            return false;
        }

        return LocalDateTime.now().isAfter(getExpirationTime(expiration, params.getReceivedAt()));
    }

    private static @NotNull LocalDateTime getExpirationTime(String expiration, LocalDateTime receivedAt) {
        return receivedAt.plusSeconds(Long.parseLong(expiration)).minusSeconds(5);
    }

    private boolean isNullOrEmpty(String... values) {
        return Arrays.stream(values).anyMatch(value -> value == null || value.isEmpty());
    }

    private Object getTokenSynchronizer(Session session) {
        var refreshToken = session.getOAuth2AnswerParameters().getRefreshToken();

        try {
            return refreshToken == null
                    ? new Object()
                    : synchronizers.get(refreshToken, Object::new);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
