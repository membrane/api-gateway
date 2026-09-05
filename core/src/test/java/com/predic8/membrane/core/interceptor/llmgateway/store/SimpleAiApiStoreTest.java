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

import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.llmgateway.store.AiApiStoreFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SimpleAiApiStoreTest {

    @Test
    void userWithoutApiKeyIsRejectedAtStartup() {
        var store = storeWith(user("Alice", null, 0));

        var e = assertThrows(ConfigurationException.class, () -> store.init(null));

        assertTrue(e.getMessage().contains("Alice"));
        assertTrue(e.getMessage().contains("apiKey"));
    }

    /**
     * The name is optional, so the message has to work without one.
     */
    @Test
    void unnamedUserWithoutApiKeyIsRejectedAtStartup() {
        var store = storeWith(user(null, null, 0));

        var e = assertThrows(ConfigurationException.class, () -> store.init(null));

        assertTrue(e.getMessage().contains("unnamed user"));
    }

    @Test
    void userIsFoundByApiKey() {
        var store = initializedStoreWith(user("Alice", "key-a", 0), user("Bob", "key-b", 0));

        assertEquals("Bob", store.getUser("key-b").orElseThrow().getName());
    }

    @Test
    void unknownApiKeyHasNoUser() {
        var store = initializedStoreWith(user("Alice", "key-a", 0));

        assertTrue(store.getUser("key-x").isEmpty());
    }

    /**
     * A client that sent no credentials at all.
     */
    @Test
    void missingApiKeyHasNoUser() {
        var store = initializedStoreWith(user("Alice", "key-a", 0));

        assertTrue(store.getUser(null).isEmpty());
    }

    @Test
    void tokensLeftAreReducedByWhatTheRequestNeeds() {
        var alice = user("Alice", "key-a", 1000);
        var store = initializedStoreWith(alice);

        assertEquals(700, store.checkLimit(alice, 200, 100));
    }

    @Test
    void userWithoutTokenBudgetIsUnlimited() {
        var alice = user("Alice", "key-a", 0);
        var store = initializedStoreWith(alice);

        assertEquals(Long.MAX_VALUE, store.checkLimit(alice, 200, 100));
    }
}
