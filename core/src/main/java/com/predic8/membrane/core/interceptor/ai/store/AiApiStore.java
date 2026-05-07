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

    void store(String user, Usage usage);

    Optional<AiApiUser> getUser(String token);

    long checkLimit(AiApiUser user);
}

