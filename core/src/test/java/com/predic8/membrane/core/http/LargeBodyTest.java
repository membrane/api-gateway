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

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.predic8.membrane.core.http.Header.CHUNKED;
import static com.predic8.membrane.core.http.Header.TRANSFER_ENCODING;

public class LargeBodyTest {

    private HttpRouter router, router2;
    private HttpClientConfiguration hcc;

    public void setup() throws Exception {
        // streaming only works for maxRetries = 1
        hcc = new HttpClientConfiguration();
        hcc.setMaxRetries(1);

        Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3040), "thomas-bayer.com", 80);
        rule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok().body("").build());
                return Outcome.RETURN;
            }
        });
        router = new HttpRouter();

        ((HTTPClientInterceptor) router.getTransport().getInterceptors().get(3)).setHttpClientConfig(hcc);

        router.getRuleManager().addProxyAndOpenPortIfNew(rule);
        router.init();

        Rule rule1 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3041), "localhost", 3040);
        router2 = new HttpRouter();

        ((HTTPClientInterceptor) router2.getTransport().getInterceptors().get(3)).setHttpClientConfig(hcc);

        router2.getRuleManager().addProxyAndOpenPortIfNew(rule1);
        router2.init();
    }

    @After
    public void shutdown() throws IOException {
        if (router != null)
            router.shutdown();
        if (router2 != null)
            router2.shutdown();
    }

    @Test
    public void large() throws Exception {
        setup();
        long len = Integer.MAX_VALUE + 1l;

        Exchange e = new Request.Builder().post("http://localhost:3041/foo").body(len, new ConstantInputStream(len)).buildExchange();
        new HttpClient(hcc).call(e);
    }

    @Test
    public void largeChunked() throws Exception {
        setup();
        long len = Integer.MAX_VALUE + 1l;

        Exchange e = new Request.Builder().post("http://localhost:3041/foo").body(len, new ConstantInputStream(len)).header(TRANSFER_ENCODING, CHUNKED).buildExchange();
        new HttpClient(hcc).call(e);
    }

    private static class ConstantInputStream extends InputStream {
        private final long len;
        long remaining;

        public ConstantInputStream(long length) {
            this.len = length;
            remaining = length;
        }

        @Override
        public int read() throws IOException {
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
                Arrays.fill(b, off, off+len, (byte)65);
                remaining -= len;
                return len;
            } else {
                return super.read(b, off, len);
            }
        }
    }
}
