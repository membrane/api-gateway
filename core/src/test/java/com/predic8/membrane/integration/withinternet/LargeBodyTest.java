/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration.withinternet;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.*;

public class LargeBodyTest {

    private static HttpRouter router, router2;
    private static HttpClientConfiguration hcc;
    private static final AtomicReference<Exchange> middleExchange = new AtomicReference<>();

    @BeforeAll
    public static void setup() throws Exception {

        // streaming only works for maxRetries = 1
        hcc = new HttpClientConfiguration();
        hcc.setMaxRetries(1);

        ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3040), "thomas-bayer.com", 80);
        proxy.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(ok().body("").build());
                return RETURN;
            }
        });
        router = new HttpRouter();

        setClientConfigHTTPClientOnInterceptor(router);

        router.getRuleManager().addProxyAndOpenPortIfNew(proxy);
        router.init();

        ServiceProxy proxy1 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3041), "localhost", 3040);
        proxy1.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                middleExchange.set(exc);
                return super.handleRequest(exc);
            }
        });
        router2 = new HttpRouter();

        setClientConfigHTTPClientOnInterceptor(router2);

        router2.getRuleManager().addProxyAndOpenPortIfNew(proxy1);
        router2.init();
    }

    private static void setClientConfigHTTPClientOnInterceptor(HttpRouter router2) {
        router2.getTransport().getFirstInterceptorOfType(HTTPClientInterceptor.class).get().setHttpClientConfig(hcc);
    }

    @AfterAll
    public static void shutdown() {
        if (router != null)
            router.shutdown();
        if (router2 != null)
            router2.shutdown();
    }

    @Test
    public void large() throws Exception {
        long len = MAX_VALUE + 1L;

        Exchange e = new Request.Builder().post("http://localhost:3041/foo").body(len, new ConstantInputStream(len)).buildExchange();
        try (HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
    }

    @Test
    public void largeChunked() throws Exception {
        long len = MAX_VALUE + 1L;

        Exchange e = new Request.Builder().post("http://localhost:3041/foo").body(len, new ConstantInputStream(len)).header(TRANSFER_ENCODING, CHUNKED).buildExchange();
        try (HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
    }

    public static class ConstantInputStream extends InputStream {
        long remaining;

        public ConstantInputStream(long length) {
            remaining = length;
        }

        @Override
        public int read() {
            if (remaining == 0)
                return -1;
            remaining--;
            return 65;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }

            if (remaining > len) {
                Arrays.fill(b, off, off + len, (byte) 65);
                remaining -= len;
                return len;
            } else {
                return super.read(b, off, len);
            }
        }
    }
}
