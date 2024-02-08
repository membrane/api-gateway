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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;

public class AccessTokenRefresher {
    private static final Logger log = LoggerFactory.getLogger(AccessTokenRefresher.class);

    private final Cache<String, Object> synchronizers = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    private AuthorizationService auth;

    public void init(AuthorizationService auth) {
        this.auth = auth;
    }

    public void refreshIfNeeded(Session session, Exchange exc) {
        if (!refreshingOfAccessTokenIsNeeded(session)) {
            return;
        }

        synchronized (getTokenSynchronizer(session)) {
            try {
                refreshAccessToken(session);
                exc.setProperty(Exchange.OAUTH2, session.getOAuth2AnswerParameters());
            } catch (Exception e) {
                log.warn("Failed to refresh access token, clearing session and restarting OAuth2 flow.", e);
                session.clearAuthentication();
            }
        }
    }

    private void refreshAccessToken(Session session) throws Exception {
        var params = session.getOAuth2AnswerParameters();
        var response = auth.refreshTokenRequest(params);

        if (!response.isOk()) {
            response.getBody().read();
            throw new RuntimeException("Statuscode from authorization server for refresh token request: " + response.getStatusCode());
        }

        if (!isJson(response)) {
            throw new RuntimeException("Refresh Token response is no JSON.");
        }

        var json = new ObjectMapper().readValue(response.getBodyAsStreamDecoded(), new TypeReference<Map<String, Object>>() {});

        if (isMissingOneToken(json)) {
            response.getBody().read();
            throw new RuntimeException("Statuscode was ok but no access_token and refresh_token was received: " + response.getStatusCode());
        }

        updateSessionTokens(session, json, params);
    }

    private boolean refreshingOfAccessTokenIsNeeded(Session session) {
        if (session.getOAuth2Answer() == null) {
            return false;
        }

        var params = session.getOAuth2AnswerParameters();
        var expiration = params.getExpiration();

        if (isNullOrEmpty(params.getRefreshToken(), expiration)) {
            return false;
        }

        var expirationTime = params.getReceivedAt().plusSeconds(Long.parseLong(expiration)).minusSeconds(5);

        return LocalDateTime.now().isAfter(expirationTime);
    }

    private boolean isNullOrEmpty(String... values) {
        return Arrays.stream(values).anyMatch(value -> value == null || value.isEmpty());
    }

    private void updateSessionTokens(Session session, Map<String, Object> json, OAuth2AnswerParameters params) throws UnsupportedEncodingException, JsonProcessingException {
        params.setAccessToken((String) json.get("access_token"));
        params.setRefreshToken((String) json.get("refresh_token"));
        params.setExpiration(numberToString(json.get("expires_in")));
        LocalDateTime now = LocalDateTime.now();
        params.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));

        if (json.containsKey("id_token")) {
            if (auth.idTokenIsValid((String) json.get("id_token"))) {
                params.setIdToken((String) json.get("id_token"));
            } else {
                params.setIdToken("INVALID");
            }
        }

        session.setOAuth2Answer(params.serialize());
    }

    private boolean isMissingOneToken(Map<String, Object> json) {
        return json.get("access_token") == null || json.get("refresh_token") == null;
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
