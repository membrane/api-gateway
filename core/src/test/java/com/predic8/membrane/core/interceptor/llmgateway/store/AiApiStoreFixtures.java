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

import java.util.List;

/**
 * Users and stores as they would come from the configuration file.
 */
public final class AiApiStoreFixtures {

    public static AiApiUser user(String name, String apiKey, long tokens) {
        var user = new AiApiUser();
        user.setName(name);
        user.setApiKey(apiKey);
        user.setTokens(tokens);
        return user;
    }

    /**
     * A store that has not been started yet, for the tests that assert on what init() rejects.
     */
    public static SimpleAiApiStore storeWith(AiApiUser... users) {
        var store = new SimpleAiApiStore();
        store.setUsers(List.of(users));
        return store;
    }

    public static SimpleAiApiStore initializedStoreWith(AiApiUser... users) {
        var store = storeWith(users);
        store.init(null);
        return store;
    }

    private AiApiStoreFixtures() {
    }
}
