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

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.log.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class PatchWithoutBodyTest {

    private DefaultRouter router;

    AtomicInteger requestSuccessfullyProcessed = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        router = createOpenApiProxy();
    }

    @AfterEach
    void tearDown() {
        if (router != null) router.stop();
    }

    @Test
    void patch_really_no_body() throws Exception {
        String resp = rawPatchNoBody("localhost", 2000, "/items/1");
        System.out.println(resp);
        assertTrue(resp.startsWith("HTTP/1.1 "), resp);
        assertEquals(1, requestSuccessfullyProcessed.get(), "HTTP Request was not successfully processed. This indicates a read timeout has happened.");
    }

    static String rawPatchNoBody(String host, int port, String path) throws Exception {
        try (Socket s = new Socket(host, port)) {
            byte[] req = ("""
                PATCH %s HTTP/1.1\r
                Host: %s:%d\r
                Connection: close\r
                \r
                """.formatted(path, host, port))
                    .getBytes(US_ASCII);

            s.getOutputStream().write(req);
            s.getOutputStream().flush();

            return new String(s.getInputStream().readAllBytes(), US_ASCII);
        }
    }

    private DefaultRouter createOpenApiProxy() throws Exception {
        DefaultRouter r = new DefaultRouter();
        r.setTransport(createHttpTransport());
        r.add(createApiProxy());
        r.start();
        return r;
    }

    private static @NotNull HttpTransport createHttpTransport() {
        HttpTransport transport = new HttpTransport();
        transport.setSocketTimeout(2000);
        return transport;
    }

    private @NotNull APIProxy createApiProxy() {
        APIProxy apiProxy = new APIProxy();
        apiProxy.setPort(2000);
        apiProxy.setFlow(new ArrayList<>(Arrays.asList(
                createLogInterceptor(),
                new AbstractInterceptor() {
                    @Override
                    public Outcome handleRequest(Exchange exc) {
                        requestSuccessfullyProcessed.set(1);
                        return Outcome.CONTINUE;
                    }
                },
                new ReturnInterceptor())));
        return apiProxy;
    }

    private static @NotNull LogInterceptor createLogInterceptor() {
        LogInterceptor li = new LogInterceptor();
        li.setBody(true);
        return li;
    }

}