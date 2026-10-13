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

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http2ClientTest {

    @Test
    void closeImmediatelyFailsPendingCall() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var config = new HttpClientConfiguration();
            config.getConnection().setKeepAliveTimeout(60_000);
            final var connection = router.getHttpClientFactory().createClient(config).getConnectionFactory()
                    .getConnectionManager().getConnection(
                            backend.getInetAddress().getHostAddress(), backend.getLocalPort(), null, null, 2000);
            // The peer accepts but never answers, so the call can only end through closeImmediately().
            try (final var ignored = backend.accept()) {
                final var client = new Http2Client(connection, false);
                final var exc = get("http://localhost/").header("Host", "localhost").buildExchange();
                final var call = CompletableFuture.supplyAsync(() -> {
                    try {
                        return client.doCall(exc, false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                client.closeImmediately();

                final var e = assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
                assertInstanceOf(IOException.class, e.getCause().getCause());
            }
        } finally {
            router.stop();
        }
    }
}
