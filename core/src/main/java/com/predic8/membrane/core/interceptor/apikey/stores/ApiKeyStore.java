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
package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.core.*;

import java.util.*;

public interface ApiKeyStore {

    /**
     * Lifecycle hook invoked once to provide the {@link Router} context.
     * Default is a no-op to preserve backward compatibility; implementations may override.
     *
     * @param router non-null router instance
     */
    default void init(Router router) {
    }

    /**
     * Validates the API key and returns the associated scopes.
     * If the key is not found, expired, or invalid, an {@link UnauthorizedApiKeyException} is thrown.
     *
     * @param apiKey the presented API key
     * @return an {@code Optional} containing the scopes for a valid key; {@code Optional.empty()} if the key is valid but has no scopes
     * @throws UnauthorizedApiKeyException if the API key is not found or is invalid
     */
    Optional<Set<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException;
}
