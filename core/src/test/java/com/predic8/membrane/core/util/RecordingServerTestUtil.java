/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.TestRouter;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

/**
 * Test helper for XXE/SSRF regression tests: a local HTTP server that flips {@code received}
 * to {@code true} the moment it is contacted, so a test can assert that a hardened parser
 * never fetched an external DTD/schema instead of only asserting that parsing failed.
 */
public class RecordingServerTestUtil {

    private RecordingServerTestUtil() {
    }

    public static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    public static TestRouter startRecordingServer(int port, AtomicBoolean received) throws IOException {
        TestRouter router = new TestRouter();
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port), "", -1);
        sp.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(com.predic8.membrane.core.exchange.Exchange exc) {
                received.set(true);
                exc.setResponse(ok("").build());
                return RETURN;
            }
        });
        router.add(sp);
        router.start();
        return router;
    }
}
