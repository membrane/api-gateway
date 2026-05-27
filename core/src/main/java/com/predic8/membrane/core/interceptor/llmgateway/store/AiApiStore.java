/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.llmgateway.store;

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
     * @param user The user to check
     * @return Estimated number of tokens that the user has left after this request
     */
    long checkLimit(AiApiUser user, long inputTokens, long outputTokens);

    long getRemainingResetTime();
}

