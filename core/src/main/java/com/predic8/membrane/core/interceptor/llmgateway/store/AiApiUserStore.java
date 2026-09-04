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

import java.util.Optional;

/**
 * A store that knows the gateway's users: it authenticates them by their API key and enforces the
 * token limit of each. A store that only records usage implements {@link AiApiStore} alone, and
 * the gateway then neither authenticates nor limits.
 */
public interface AiApiUserStore extends AiApiStore {

    /**
     * @param token the API key sent by the client
     * @return the user that key belongs to, empty if it belongs to none
     */
    Optional<AiApiUser> getUser(String token);

    /**
     * Checks if the user has enough tokens to make the request.
     * @param user The user to check
     * @return Estimated number of tokens that the user has left after this request
     */
    long checkLimit(AiApiUser user, long inputTokens, long outputTokens);

    long getRemainingResetTime();
}
