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
package com.predic8.membrane.core.rules;

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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(Parameterized.class)
public class ProxySSLTest {
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { false, false, 3032, 3033 }, { false, true, 3034, 3035 }, { true, false, 3036, 3037 }, { true, true, 3038, 3039 }
        });
    }

    private boolean backendUsesSSL;
    private boolean proxyUsesSSL;
    private int backendPort;
    private int proxyPort;

    public ProxySSLTest(boolean backendUsesSSL, boolean proxyUsesSSL, int backendPort, int proxyPort) {
        this.backendUsesSSL = backendUsesSSL;
        this.proxyUsesSSL = proxyUsesSSL;
        this.backendPort = backendPort;
        this.proxyPort = proxyPort;
    }

    @Test
    public void test() throws Exception {
        // Step 1: create the backend
        Router backend = new Router();
        backend.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(backendPort), null, 0);
        if (backendUsesSSL) {
            SSLParser ssl = new SSLParser();

            ssl.setKeyStore(new KeyStore());
            ssl.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
            ssl.getKeyStore().setKeyPassword("secret");

            sp.setSslInboundParser(ssl);
        }
        sp.getInterceptors().add(new CountInterceptor());
        backend.getRuleManager().addProxy(sp, RuleManager.RuleDefinitionSource.MANUAL);
        backend.start();

        // Step 2: put a proxy in front of it
        final AtomicInteger proxyCounter = new AtomicInteger();

        Router proxyRouter = new Router();
        proxyRouter.setHotDeploy(false);
        ProxyRule proxy = new ProxyRule(new ProxyRuleKey(proxyPort));
        proxy.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                proxyCounter.incrementAndGet();
                return super.handleRequest(exc);
            }
        });
        if (proxyUsesSSL) {
            SSLParser ssl = new SSLParser();

            ssl.setKeyStore(new KeyStore());
            ssl.getKeyStore().setLocation("classpath:/ssl-rsa2.keystore");
            ssl.getKeyStore().setKeyPassword("secret");

            proxy.setSslInboundParser(ssl);
        }
        proxyRouter.getRuleManager().addProxy(proxy, RuleManager.RuleDefinitionSource.MANUAL);
        proxyRouter.start();

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
        HttpClient hc = new HttpClient(httpClientConfiguration);

        // Step 4: Test client
        Exchange exc = new Request.Builder().get("http" + (backendUsesSSL ? "s" : "") + "://localhost:" + backendPort + "/foo").buildExchange();
        if (backendUsesSSL) {
            SSLParser ssl = new SSLParser();
            ssl.setTrustStore(new TrustStore());
            ssl.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
            ssl.getTrustStore().setPassword("secret");
            ssl.setEndpointIdentificationAlgorithm(""); // workarond the fact that the certificate was not issued for 'localhost'
            exc.setProperty(Exchange.SSL_CONTEXT, new StaticSSLContext(ssl, new ResolverMap(), null));
        }
        hc.call(exc);

        Assert.assertEquals(200, exc.getResponse().getStatusCode());
        Assert.assertEquals("Did the request go through the proxy?", 1, proxyCounter.get());

        proxyRouter.shutdown();
        backend.shutdown();
    }

}
