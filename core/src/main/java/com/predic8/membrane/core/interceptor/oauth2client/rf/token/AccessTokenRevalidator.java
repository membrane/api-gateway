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
import java.util.Optional;
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

    public void revalidateIfNeeded(Session session) throws Exception {
        if (!tokenNeedsRevalidation(session)) {
            return;
        }

        revalidate(session).ifPresentOrElse(
                valid -> statistics.accessTokenValid(),
                () -> {
                    statistics.accessTokenInvalid();
                    session.clear();
                }
        );
    }

    public Optional<Map<String, Object>> revalidate(Session session) throws Exception {
        Response response = auth.requestUserEndpoint(session.getOAuth2AnswerParameters());

        if (response.getStatusCode() != 200) {
            return Optional.empty();
        } else {
            if (!JsonUtils.isJson(response))
                throw new RuntimeException("Response is no JSON.");
            return Optional.ofNullable(new ObjectMapper().readValue(response.getBodyAsStreamDecoded(), new TypeReference<>() {
            }));
        }
    }

    private boolean tokenNeedsRevalidation(Session session) {
        if (!session.hasOAuth2Answer() || revalidateTokenAfter < 0) {
            return false;
        }

        return validTokens.getIfPresent(session.getAccessToken()) == null;
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
