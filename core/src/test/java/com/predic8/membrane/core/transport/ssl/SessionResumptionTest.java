/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.exchange.Exchange.SSL_CONTEXT;
import static com.predic8.membrane.core.http.Header.CONTENT_ENCODING;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * This test provokes a failing TLS 1.3 Session Resumption by transmitting a "pre_shared_key (41)" extension in the
 * second TLS handshake, but actually forwarding the second TCP connection to another SSLContext instance.
 * <p>
 * Use "-Djavax.net.debug=all" on the test execution to see the details.
 */
public class SessionResumptionTest {

    private static Closeable tcpForwarder;
    private static Router router1;
    private static Router router2;
    private static SSLContext clientTLSContext;

    @BeforeAll
    public static void init() throws IOException {
        tcpForwarder = startTCPForwarder(3044);
        router1 = createTLSServer(3042);
        router2 = createTLSServer(3043);

        clientTLSContext = createClientTLSContext();
    }

    private static SSLContext createClientTLSContext() {
        SSLParser sslParser = new SSLParser();
        TrustStore trustStore = new TrustStore();
        trustStore.setLocation("classpath:/ssl-rsa-pub.keystore");
        trustStore.setPassword("secret");
        sslParser.setTrustStore(trustStore);
        sslParser.setEndpointIdentificationAlgorithm("");
        return new StaticSSLContext(sslParser, new ResolverMap(), ".");
    }

    @AfterAll
    public static void done() throws IOException {
        tcpForwarder.close();
        router1.shutdown();
        router2.shutdown();
    }

    @Disabled
    @Test
    public void doit() throws Exception {
        try(HttpClient hc = new HttpClient()) {
            for (int i = 0; i < 2; i++) {
                // the tcp forwarder will forward the first connection to 3042, the second to 3043
                Exchange exc = new Request.Builder().get("https://localhost:3044").buildExchange();
                exc.setProperty(SSL_CONTEXT, clientTLSContext);
                hc.call(exc);
            }
        }
    }

    private static Router createTLSServer(int port) {
        Router router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy rule = new ServiceProxy(new ServiceProxyKey(port), null, 0);
        SSLParser sslInboundParser = new SSLParser();
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation("classpath:/ssl-rsa.keystore");
        keyStore.setKeyPassword("secret");
        sslInboundParser.setKeyStore(keyStore);

        rule.setSslInboundParser(sslInboundParser);
        rule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                // Inlined from Exchange. Maybe use EchoIntercepor
                Response.ResponseBuilder builder = Response.ok();
                byte[] content;
                try {
                    content = exc.getRequest().getBody().getContent();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                builder.body(content);
                String contentType = exc.getRequest().getHeader().getContentType();
                if (contentType != null)
                    builder.header(CONTENT_TYPE, contentType);
                String contentEncoding = exc.getRequest().getHeader().getContentEncoding();
                if (contentEncoding != null)
                    builder.header(CONTENT_ENCODING, contentEncoding);
                exc.setResponse(builder.build());
                exc.getResponse().getHeader().add("Connection", "close");
                return RETURN;
            }
        });
        router.getRuleManager().addProxy(rule, RuleManager.RuleDefinitionSource.MANUAL);
        router.start();
        return router;
    }

    private static Closeable startTCPForwarder(int port) throws IOException {
        AtomicInteger counter = new AtomicInteger();
        StreamPump.StreamPumpStats sps = new StreamPump.StreamPumpStats();
        ServiceProxy mock = new ServiceProxy();
        try (ServerSocket ss = new ServerSocket(port)) {
            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        Socket inbound;
                        try {
                            inbound = ss.accept();
                        } catch (Exception e) {
                            if (e.getMessage().contains("Socket closed"))
                                return;
                            throw e;
                        }
                        int port1 = 3042 + counter.incrementAndGet() % 2;
                        try(Socket outbout = new Socket("localhost", port1)) {
                            Thread s = new Thread(new StreamPump(inbound.getInputStream(), outbout.getOutputStream(), sps, "a", mock));
                            s.start();
                            Thread s2 = new Thread(new StreamPump(outbout.getInputStream(), inbound.getOutputStream(), sps, "b", mock));
                            s2.start();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            t.start();
            return () -> {
                ss.close();
                t.interrupt();
            };
        }
    }
}
