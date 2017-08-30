package com.predic8.membrane.core.http;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.junit.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.predic8.membrane.core.http.Header.CHUNKED;
import static com.predic8.membrane.core.http.Header.TRANSFER_ENCODING;

public class LargeBodyTest {

    private HttpRouter router, router2;

    public void setup() throws Exception {
        Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3040), "thomas-bayer.com", 80);
        rule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok().body("").build());
                return Outcome.RETURN;
            }
        });
        router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(rule);
        router.init();

        Rule rule1 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3041), "localhost", 3040);
        router2 = new HttpRouter();
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
        new HttpClient().call(e);
    }

    @Test
    public void largeChunked() throws Exception {
        setup();
        long len = Integer.MAX_VALUE + 1l;

        Exchange e = new Request.Builder().post("http://localhost:3041/foo").body(len, new ConstantInputStream(len)).header(TRANSFER_ENCODING, CHUNKED).buildExchange();
        new HttpClient().call(e);
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
