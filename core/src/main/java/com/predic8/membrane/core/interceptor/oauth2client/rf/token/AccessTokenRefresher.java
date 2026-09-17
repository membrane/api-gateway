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
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.session.Session;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static com.predic8.membrane.core.exchange.Exchange.OAUTH2;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.communicationError;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor.WANTED_SCOPE;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.SECONDS;

public class AccessTokenRefresher {
    private static final Logger log = LoggerFactory.getLogger(AccessTokenRefresher.class);

    // weakKeys() ties the entry lifetime to the Session object: the entry is evicted only
    // when the Session is GC'd, which cannot happen while any thread holds a reference to it.
    // No expireAfterAccess: time-based eviction could replace a monitor while a slow
    // refreshTokenRequest holds it, letting a second thread acquire a different lock object.
    private final Cache<Session, Object> synchronizers = CacheBuilder.newBuilder()
            .weakKeys()
            .build();

    /**
     * How long a session stops asking after the authorization server was found unreachable.
     */
    static final int UNREACHABLE_BACKOFF_SECONDS = 5;

    // Marks the sessions whose last refresh ran into an unreachable authorization server. Without it
    // every further request of such a session queues up on the monitor below and waits out its own
    // connect timeout, so N pending requests cost N timeouts and keep hammering a server that is
    // already down. An entry expires by itself, so the first request after the window tries again.
    private final Cache<Session, Boolean> unreachable = CacheBuilder.newBuilder()
            .weakKeys()
            .expireAfterWrite(UNREACHABLE_BACKOFF_SECONDS, SECONDS)
            .build();

    private AuthorizationService auth;
    private boolean onlyRefreshToken;

    public void init(AuthorizationService auth, boolean onlyRefreshToken) {
        this.auth = auth;
        this.onlyRefreshToken = onlyRefreshToken;
    }

    /**
     * @throws OAuth2Exception if the authorization server could not be reached. The session stays
     *                         authenticated in that case - see {@link #isUnreachable(OAuth2Exception)}.
     *                         Further requests of the same session fail right away for
     *                         {@link #UNREACHABLE_BACKOFF_SECONDS} seconds instead of asking again.
     */
    public void refreshIfNeeded(Session session, Exchange exc) throws OAuth2Exception {
        String wantedScope = exc.getProperty(WANTED_SCOPE, String.class);
        if (!refreshingOfAccessTokenIsNeeded(session, wantedScope)) {
            return;
        }

        failFastWhileUnreachable(session);

        synchronized (getTokenSynchronizer(session)) {
            // a concurrent caller may have already refreshed the token
            // while this thread waited for the monitor.
            if (!refreshingOfAccessTokenIsNeeded(session, wantedScope)) {
                return;
            }
            failFastWhileUnreachable(session);
            try {
                exc.setProperty(OAUTH2, refreshAccessToken(session, wantedScope));
                unreachable.invalidate(session);
            } catch (Exception e) {
                if (e instanceof OAuth2Exception oauth2 && isUnreachable(oauth2)) {
                    log.warn("Could not reach the authorization server to refresh the access token. " +
                             "Keeping the session, the request fails instead.", e);
                    unreachable.put(session, TRUE);
                    throw oauth2;
                }
                log.warn("Failed to refresh access token, clearing session and restarting OAuth2 flow.", e);
                session.clearAuthentication();
            }
        }
    }

    private void failFastWhileUnreachable(Session session) throws OAuth2Exception {
        if (unreachable.getIfPresent(session) == null) {
            return;
        }
        log.debug("Authorization server was unreachable within the last {} s, not asking again.", UNREACHABLE_BACKOFF_SECONDS);
        throw communicationError();
    }

    private static boolean isUnreachable(OAuth2Exception e) {
        return MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR.equals(e.getError());
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
        try {
            return synchronizers.get(session, Object::new);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
