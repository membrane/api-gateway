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

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.interceptor.stomp.STOMPClient;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class STOMPProxyTest {

    private static final String CONNECTED = "CONNECTED\nversion:1.2\n\n\0";

    private ServerSocket broker;
    private DefaultRouter router;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        broker = new ServerSocket(0);
        port = freePort();

        final var client = new STOMPClient();
        client.setHost("localhost");
        client.setPort(broker.getLocalPort());

        final var proxy = new STOMPProxy();
        proxy.setPort(port);
        proxy.setFlow(new ArrayList<>(List.of(client)));

        final var transport = new HttpTransport();
        transport.setSocketTimeout(5000);
        router = new DefaultRouter();
        router.setTransport(transport);
        router.add(proxy);
        router.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (router != null)
            router.stop();
        broker.close();
    }

    /**
     * STOMP 1.2 "CONNECT or STOMP Frame": servers MUST handle a STOMP frame in the same manner as a CONNECT frame.
     */
    @ParameterizedTest
    @ValueSource(strings = {"CONNECT", "STOMP"})
    void connectFrameIsForwardedToTheBrokerAndTheSessionIsTunneled(String command) throws Exception {
        broker.setSoTimeout(5000);
        final var brokerSide = CompletableFuture.supplyAsync(() -> {
            try (var s = broker.accept()) {
                s.setSoTimeout(5000);
                final var frame = readFrame(s.getInputStream());
                s.getOutputStream().write(CONNECTED.getBytes(US_ASCII));
                s.getOutputStream().flush();
                // Proves that the connection is tunneled after the CONNECT/STOMP frame
                final var next = readFrame(s.getInputStream());
                return frame + next;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try (var s = new Socket("localhost", port)) {
            s.setSoTimeout(5000);
            s.getOutputStream().write("%s\naccept-version:1.2\nhost:localhost\n\n\0".formatted(command).getBytes(US_ASCII));
            s.getOutputStream().flush();

            assertEquals(CONNECTED, readFrame(s.getInputStream()));

            s.getOutputStream().write("DISCONNECT\nreceipt:1\n\n\0".getBytes(US_ASCII));
            s.getOutputStream().flush();

            final var received = brokerSide.get(5, SECONDS);
            assertTrue(received.startsWith(command + "\n"), received);
            assertTrue(received.contains("accept-version:1.2\n"), received);
            assertTrue(received.contains("DISCONNECT\nreceipt:1\n"), received);
        }
    }

    /**
     * Reads one STOMP frame including the terminating NUL.
     */
    private static String readFrame(InputStream in) throws IOException {
        final var frame = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            frame.write(b);
            if (b == 0)
                break;
        }
        return frame.toString(US_ASCII);
    }
}
