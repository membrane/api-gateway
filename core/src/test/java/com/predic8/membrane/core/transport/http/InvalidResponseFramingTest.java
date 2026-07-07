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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static com.predic8.membrane.annot.Constants.CRLF;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * A backend response with conflicting Content-Length headers (RFC 9112 §6.3)
 * must not be forwarded. The gateway rejects it with 502 Bad Gateway — as opposed
 * to a malformed *request*, which is answered with 400 by the HttpServerHandler.
 */
class InvalidResponseFramingTest {

    private static final int FRONTEND_PORT = 3070;
    private static final int BACKEND_PORT  = 3071;

    private Router router;
    private ServerSocket backend;
    private volatile boolean running;

    @BeforeEach
    void setUp() throws IOException {
        // Raw backend: answers every connection with two conflicting Content-Length values.
        // An accept-loop is used because the client retries idempotent GETs (retries=2 by default).
        backend = new ServerSocket(BACKEND_PORT);
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try (Socket s = backend.accept()) {
                    OutputStream out = s.getOutputStream();
                    out.write((
                            "HTTP/1.1 200 OK" + CRLF +
                            "Content-Type: text/plain" + CRLF +
                            "Content-Length: 5" + CRLF +
                            "Content-Length: 6" + CRLF +
                            "Connection: close" + CRLF +
                            CRLF +
                            "hello").getBytes(US_ASCII));
                    out.flush();
                } catch (IOException e) {
                    // ServerSocket closed on teardown -> leave the loop.
                }
            }
        });
        t.setDaemon(true);
        t.start();

        router = new TestRouter();
        router.add(new ServiceProxy(new ServiceProxyKey(FRONTEND_PORT), "localhost", BACKEND_PORT));
        router.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        running = false;
        backend.close();
        router.stop();
    }

    @Test
    void backendWithConflictingContentLengthYields502() throws Exception {
        try (HttpAssertions ha = new HttpAssertions()) {
            ha.getAndAssert(502, "http://localhost:" + FRONTEND_PORT + "/");
        }
    }
}
