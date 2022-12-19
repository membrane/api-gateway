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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.config.security.Key;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.StreamPump;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

/**
 * This test provokes a failing TLS 1.3 Session Resumption by transmitting a "pre_shared_key (41)" extension in the
 * second TLS handshake, but actually forwarding the second TCP connection to another SSLContext instance.
 *
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
        HttpClient hc = new HttpClient();
        for (int i = 0; i < 2; i++) {
            // the tcp forwarder will forward the first connection to 3042, the second to 3043
            Exchange exc = new Request.Builder().get("https://localhost:3044").buildExchange();
            exc.setProperty(Exchange.SSL_CONTEXT, clientTLSContext);
            hc.call(exc);
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
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.echo();
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
        ServerSocket ss = new ServerSocket(port);
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    while(true) {
                        Socket inbound;
                        try {
                            inbound = ss.accept();
                        } catch (Exception e) {
                            if (e.getMessage().contains("Socket closed"))
                                return;
                            throw e;
                        }
                        int port = 3042 + counter.incrementAndGet() % 2;
                        Socket outbout = new Socket("localhost", port);
                        Thread s = new Thread(new StreamPump(inbound.getInputStream(), outbout.getOutputStream(), sps, "a", mock));
                        s.start();
                        Thread s2 = new Thread(new StreamPump(outbout.getInputStream(), inbound.getOutputStream(), sps, "b", mock));
                        s2.start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
        return new Closeable() {
            @Override
            public void close() throws IOException {
                ss.close();
                t.interrupt();
            }
        };
    }
}
