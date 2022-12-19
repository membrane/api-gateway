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

package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProxyTest {

    static HttpRouter router;
    static AtomicReference<String> lastMethod = new AtomicReference<>();

    @BeforeAll
    public static void init() throws Exception {

        router = new HttpRouter();
        router.setHotDeploy(false);

        ProxyRule rule = new ProxyRule(new ProxyRuleKey(3055));
        rule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                lastMethod.set(exc.getRequest().getMethod());
                return super.handleRequest(exc);
            }
        });
        router.getRules().add(rule);

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3056), null, 0);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok("secret1").build());
                return Outcome.RETURN;
            }
        });
        router.getRules().add(sp);

        SSLParser sslParser = new SSLParser();
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        sslParser.getKeyStore().setKeyPassword("secret");
        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey(3057), null, 0);
        sp2.setSslInboundParser(sslParser);
        sp2.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok("secret2").build());
                return Outcome.RETURN;
            }
        });
        router.getRules().add(sp2);

        router.start();
    }

    @AfterAll
    public void done() throws IOException {
        router.shutdown();
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
        Scheme https = new Scheme("https", 3057, sslsf);

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
