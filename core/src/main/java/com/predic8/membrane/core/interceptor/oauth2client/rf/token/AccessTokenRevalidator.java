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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils;
import com.predic8.membrane.core.interceptor.session.Session;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AccessTokenRevalidator {

    private final Cache<String, Boolean> validTokens = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private int revalidateTokenAfter = -1;
    private AuthorizationService auth;
    private OAuth2Statistics statistics;

    public void init(AuthorizationService auth, OAuth2Statistics statistics) {
        this.auth = auth;
        this.statistics = statistics;
    }

    public void revalidateIfNeeded(Session session, String wantedScope) throws Exception {
        if (!tokenNeedsRevalidation(session, wantedScope)) {
            return;
        }

        if (revalidate(session, statistics, wantedScope) == null) {
            session.clear();
        }
    }

    public Map<String, Object> revalidate(Session session, OAuth2Statistics statistics, String wantedScope) throws Exception {
        Response response = auth.requestUserEndpoint(session.getOAuth2AnswerParameters(wantedScope));

        if (response.getStatusCode() != 200) {
            statistics.accessTokenValid();
            return null;
        } else {
            statistics.accessTokenValid();

            if (!JsonUtils.isJson(response)) {
                throw new RuntimeException("Response is no JSON.");
            }

            return new ObjectMapper().readValue(response.getBodyAsStreamDecoded(), new TypeReference<>() {});
        }
    }

    private boolean tokenNeedsRevalidation(Session session, String wantedScope) {
        if (!session.hasOAuth2Answer(wantedScope) || revalidateTokenAfter < 0) {
            return false;
        }

        return validTokens.getIfPresent(session.getAccessToken(wantedScope)) == null;
    }

    public Cache<String, Boolean> getValidTokens() {
        return validTokens;
    }

    public int getRevalidateTokenAfter() {
        return revalidateTokenAfter;
    }

    public void setRevalidateTokenAfter(int revalidateTokenAfter) {
        this.revalidateTokenAfter = revalidateTokenAfter;
    }
}
