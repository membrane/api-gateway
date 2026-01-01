/* Copyright 2022 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.router.*;
import org.apache.http.*;
import org.apache.http.client.config.*;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.impl.client.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.security.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProxyTest {

    static Router router;
    static final AtomicReference<String> lastMethod = new AtomicReference<>();

    @BeforeAll
    public static void init() throws IOException {

        router = new TestRouter();
        router.getConfiguration().setHotDeploy(false);

        ProxyRule rule = new ProxyRule(new ProxyRuleKey(3055));
        rule.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                lastMethod.set(exc.getRequest().getMethod());
                return super.handleRequest(exc);
            }
        });
        router.add(rule);

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3056), null, 0);
        sp.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok("secret1").build());
                return RETURN;
            }
        });
        router.add(sp);

        SSLParser sslParser = new SSLParser();
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        sslParser.getKeyStore().setKeyPassword("secret");
        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey(3057), null, 0);
        sp2.setSslInboundParser(sslParser);
        sp2.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok("secret2").build());
                return RETURN;
            }
        });
        router.add(sp2);

        router.start();
    }

    @AfterAll
    public static void done() {
        router.stop();
    }

    @Test
    public void runHTTP() throws IOException {
        CloseableHttpClient hc = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet("http://localhost:3056");
        httpGet.setConfig(RequestConfig.custom()
                .setProxy(new HttpHost("localhost", 3055))
                .build());
        CloseableHttpResponse response = hc.execute(httpGet);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertTrue(EntityUtils.toString(response.getEntity()).contains("secret1"));
        assertEquals("GET", lastMethod.get());

        hc.close();
    }

    @Test
    public void runHTTPS() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, InterruptedException {
        SSLSocketFactory sslsf = new SSLSocketFactory("TLS", null, null, null, null,
                new TrustAllStrategy(), new AllowAllHostnameVerifier());

        CloseableHttpClient hc = HttpClientBuilder.create()
                .setSSLSocketFactory(sslsf)
                .build();
        HttpGet httpGet = new HttpGet("https://localhost:3057");
        httpGet.setConfig(RequestConfig.custom()
                .setProxy(new HttpHost("localhost", 3055))
                .build());
        CloseableHttpResponse response = hc.execute(httpGet);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertTrue(EntityUtils.toString(response.getEntity()).contains("secret2"));
        assertEquals("CONNECT", lastMethod.get());

        hc.close();
        Thread.sleep(100);

        assertEquals("0", router.getTransport().getOpenBackendConnections(3055));
        assertEquals("0", router.getTransport().getOpenBackendConnections(3057));
    }

}
