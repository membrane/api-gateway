/* Copyright 2016 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.CountInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.jetbrains.annotations.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.exchange.Exchange.SSL_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProxySSLTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { false, false, 3032, 3033 }, { false, true, 3034, 3035 }, { true, false, 3036, 3037 }, { true, true, 3038, 3039 }
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    void test(boolean backendUsesSSL, boolean proxyUsesSSL, int backendPort, int proxyPort) throws Exception {
        Router backend = createBackend(backendUsesSSL, backendPort);

        AtomicInteger proxyCounter = new AtomicInteger();

        Router proxy = createProxy(proxyUsesSSL, proxyPort, proxyCounter);

        testClient(backendUsesSSL, backendPort, createAndConfigureClient(proxyUsesSSL, proxyPort), proxyCounter);

        proxy.shutdown();
        backend.shutdown();
    }

    private static @NotNull Router createProxy(boolean proxyUsesSSL, int proxyPort, AtomicInteger proxyCounter) {
        Router proxy = new Router();
        proxy.setHotDeploy(false);
        ProxyRule rule = new ProxyRule(new ProxyRuleKey(proxyPort));
        rule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                proxyCounter.incrementAndGet();
                return super.handleRequest(exc);
            }
        });
        if (proxyUsesSSL) {
            rule.setSslInboundParser(getSslParser("classpath:/ssl-rsa2.keystore"));
        }
        proxy.getRuleManager().addProxy(rule, RuleManager.RuleDefinitionSource.MANUAL);
        proxy.start();
        return proxy;
    }

    private static void testClient(boolean backendUsesSSL, int backendPort, HttpClient hc, AtomicInteger proxyCounter) throws Exception {
        // Step 4: Test client
        Exchange exc = new Request.Builder().get("http" + (backendUsesSSL ? "s" : "") + "://localhost:" + backendPort + "/foo").buildExchange();
        if (backendUsesSSL) {
            SSLParser ssl = new SSLParser();
            ssl.setTrustStore(new TrustStore());
            ssl.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
            ssl.getTrustStore().setPassword("secret");
            ssl.setEndpointIdentificationAlgorithm(""); // workarond the fact that the certificate was not issued for 'localhost'
            exc.setProperty(SSL_CONTEXT, new StaticSSLContext(ssl, new ResolverMap(), null));
        }
        hc.call(exc);

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals(1, proxyCounter.get(), "Did the request go through the proxy?");
    }

    private static @NotNull HttpClient createAndConfigureClient(boolean proxyUsesSSL, int proxyPort) {
        // Step 3: configure the client to access the backend through the proxy
        HttpClientConfiguration httpClientConfiguration = new HttpClientConfiguration();
        ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
        proxyConfiguration.setHost("localhost");
        proxyConfiguration.setPort(proxyPort);
        if (proxyUsesSSL) {
            SSLParser ssl = new SSLParser();
            ssl.setTrustStore(new TrustStore());
            ssl.getTrustStore().setLocation("classpath:/ssl-rsa-pub2.keystore");
            ssl.getTrustStore().setPassword("secret");
            ssl.setEndpointIdentificationAlgorithm(""); // workarond the fact that the certificate was not issued for 'localhost'
            proxyConfiguration.setSslParser(ssl);
        }
        httpClientConfiguration.setProxy(proxyConfiguration);
        return new HttpClient(httpClientConfiguration);
    }

    private static @NotNull Router createBackend(boolean backendUsesSSL, int backendPort) {
        // Step 1: create the backend
        Router backend = new Router();
        backend.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(backendPort), null, 0);
        if (backendUsesSSL) {
            sp.setSslInboundParser(getSslParser("classpath:/ssl-rsa.keystore"));
        }
        sp.getInterceptors().add(new CountInterceptor());
        backend.getRuleManager().addProxy(sp, RuleManager.RuleDefinitionSource.MANUAL);
        backend.start();
        return backend;
    }

    private static @NotNull SSLParser getSslParser(String location) {
        SSLParser ssl = new SSLParser();
        ssl.setKeyStore(new KeyStore());
        ssl.getKeyStore().setLocation(location);
        ssl.getKeyStore().setKeyPassword("secret");
        return ssl;
    }

}
