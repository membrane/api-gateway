package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.core.router.Router;

import java.util.Optional;

/**
 * @TODO
 * - Store .status, .error, .model, .stop_reason
 */
public interface AiApiStore {

    default void init(Router router) {
    }

    void store(AiApiUser user, Usage usage);

    Optional<AiApiUser> getUser(String token);

    /**
     * Checks if the user has enough tokens to make the request.
     * @param user
     * @return
     */
    long checkLimit(AiApiUser user, long inputTokens, long outputTokens);
}

