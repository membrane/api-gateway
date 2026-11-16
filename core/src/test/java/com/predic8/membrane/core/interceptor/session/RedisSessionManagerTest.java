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

package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.*;
import redis.clients.jedis.params.*;

import static org.mockito.Mockito.*;

class RedisSessionManagerTest {

    /**
     * Reading a session must extend its life by the session manager's expiry, not by the connector's
     * connection timeout (#3779).
     */
    @Test
    void readRefreshesTtlToSessionExpiry() {
        final var jedis = mock(Jedis.class);
        final var connector = mock(RedisConnector.class);
        when(connector.getJedisWithDb()).thenReturn(jedis);

        final var manager = new RedisSessionManager();
        manager.setConnector(connector);
        manager.setExpiresAfterSeconds(60);

        manager.readAndRefreshTtl("session-key");

        verify(jedis).getEx("session-key", new GetExParams().ex(60));
    }
}
