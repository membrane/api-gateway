/* Copyright 2014 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class HttpsKeepAliveTest {

    private static HttpRouter server;

    private static final ConcurrentHashMap<String, Boolean> connectionHashes = new ConcurrentHashMap<>();

    @BeforeAll
    public static void startServer() {
        server = new HttpRouter();
        server.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy();
        sp.setPort(3063);
        SSLParser sslIB = new SSLParser();
        KeyStore ksIB = new KeyStore();
        ksIB.setLocation("classpath:/alias-keystore.p12");
        ksIB.setKeyPassword("secret");
        ksIB.setKeyAlias("key1");
        sslIB.setKeyStore(ksIB);
        sp.setSslInboundParser(sslIB);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok("ssltest").build());
                connectionHashes.put("" + ((HttpServerHandler)exc.getHandler()).getSrcOut().hashCode(), true);
                return RETURN;
            }
        });
        server.getRules().add(sp);
        server.start();
    }

    private static StaticSSLContext createSSLOutboundContext() {
        SSLParser sslOB = new SSLParser();
        sslOB.setEndpointIdentificationAlgorithm("");
        TrustStore tsOB = new TrustStore();
        tsOB.setLocation("classpath:/alias-truststore.p12");
        tsOB.setPassword("secret");
        sslOB.setTrustStore(tsOB);
        return new StaticSSLContext(sslOB, new ResolverMap(), "/");
    }

    @AfterAll
    public static void shutdownServer() {
        server.stop();
    }

    @Test
    void httpsKeepAlive() throws Exception {
        SSLContext sslCtxOb = createSSLOutboundContext();
        try (HttpClient hc = new HttpClient()) {
            for (int i = 0; i < 2; i++) {
                Exchange exc1 = get("https://localhost:3063/").buildExchange();
                exc1.setProperty(SSL_CONTEXT, sslCtxOb);
                hc.call(exc1);

                assertEquals(200, exc1.getResponse().getStatusCode());
                assertEquals("ssltest", exc1.getResponse().getBodyAsStringDecoded());
            }
        }

        assertEquals(1, connectionHashes.size());
    }
}
