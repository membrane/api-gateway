package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;

public class AccessTokenRevalidator {

    private final Cache<String, Boolean> validTokens = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
    private int revalidateTokenAfter = -1;

    public boolean tokenNeedsRevalidation(String token) {
        if (revalidateTokenAfter < 0) return false;
        return validTokens.getIfPresent(token) == null;
    }

    public Cache<String, Boolean> getValidTokens() {
        return validTokens;
    }
}
