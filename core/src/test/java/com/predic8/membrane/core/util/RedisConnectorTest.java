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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisConnectorTest {

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
