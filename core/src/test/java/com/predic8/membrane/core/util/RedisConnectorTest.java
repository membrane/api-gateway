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
package com.predic8.membrane.core.util;

import com.predic8.membrane.annot.yaml.parsing.support.BeanLifecycleInvoker;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class RedisConnectorTest {

    @Test
    void connectionIsReturnedWhenSelectFails() throws Exception {
        final var jedis = mock(Jedis.class);
        doThrow(new JedisConnectionException("Connection reset")).when(jedis).select(anyInt());
        final var pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        final var connector = new RedisConnector();
        final var poolField = RedisConnector.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        poolField.set(connector, pool);

        assertThrows(JedisConnectionException.class, connector::getJedisWithDb);
        verify(jedis).close();
    }

    @Test
    void yamlLifecycleCreatesPool() {
        final var connector = new RedisConnector();

        new BeanLifecycleInvoker().apply(null, connector);

        assertNotNull(connector.getPool());
    }

    private static final int TLS_HANDSHAKE_RECORD = 0x16;
    private static final int RESP_ARRAY = '*';

    @Test
    void sentinelConnectionUsesTlsWhenSslIsSet() throws Exception {
        assertEquals(TLS_HANDSHAKE_RECORD, firstByteSentToSentinel(true));
    }

    @Test
    void sentinelConnectionIsPlainWithoutSsl() throws Exception {
        assertEquals(RESP_ARRAY, firstByteSentToSentinel(false));
    }

    /**
     * Starts a fake Sentinel that records the first byte a client sends and then closes the
     * connection. The connector cannot reach a real Sentinel, so its setup fails afterwards.
     */
    private static int firstByteSentToSentinel(boolean ssl) throws Exception {
        try (final var sentinel = new ServerSocket(0);
             final var executor = Executors.newSingleThreadExecutor()) {
            final Future<Integer> firstByte = executor.submit(() -> readFirstByte(sentinel));

            final var connector = new RedisConnector();
            connector.setUseSentinels(true);
            connector.setSsl(ssl);
            connector.setHost("localhost");
            connector.setPort(sentinel.getLocalPort());
            connector.setTimeout(2000);
            try {
                connector.afterPropertiesSet();
            } catch (Exception ignored) {
                // expected: the fake Sentinel never answers
            }
            return firstByte.get(10, TimeUnit.SECONDS);
        }
    }

    private static int readFirstByte(ServerSocket sentinel) throws IOException {
        try (final var client = sentinel.accept()) {
            return client.getInputStream().read();
        }
    }
}
