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
package com.predic8.membrane.core.transport.ssl.acme;

import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.Certificate;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.config.security.acme.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.bouncycastle.jce.provider.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.security.*;
import java.util.*;

import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.transport.ssl.acme.Authorization.*;
import static com.predic8.membrane.core.transport.ssl.acme.Order.*;
import static org.junit.jupiter.api.Assertions.*;

public class AcmeStepTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private AcmeServerSimulator sim;
    public final String baseUrl = "http://localhost:3050/directory";

    @BeforeEach
    public void init() throws IOException {
        sim = new AcmeServerSimulator(3050, 3052, true);
        sim.start();
    }

    @AfterEach
    public void done() {
        sim.stop();
    }

    @Test
    public void all() throws Exception {
        Acme acme = new Acme();
        acme.setExperimental(true);
        acme.setDirectoryUrl(baseUrl);
        acme.setTermsOfServiceAgreed(true);
        acme.setContacts("mailto:jsmith@example.com");
        acme.setAcmeSynchronizedStorage(new MemoryStorage());

        HttpRouter router = new HttpRouter();
        router.setHotDeploy(false);
        SSLParser sslParser = new SSLParser();
        sslParser.setAcme(acme);
        ServiceProxy sp1 = new ServiceProxy(new ServiceProxyKey(3051), "localhost", 80);
        sp1.setHost("localhost example.com");
        sp1.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(ok().status(234, "Successful test.").build());
                return RETURN;
            }
        });
        sp1.setSslInboundParser(sslParser);
        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey(3052), "localhost", 80);
        AcmeHttpChallengeInterceptor acmeHttpChallengeInterceptor = new AcmeHttpChallengeInterceptor();
        acmeHttpChallengeInterceptor.setIgnorePort(true);
        sp2.getFlow().add(acmeHttpChallengeInterceptor);
        router.setRules(ImmutableList.of(sp1, sp2));
        router.start();
        try {

            AcmeSSLContext acmeSSLContext = (AcmeSSLContext) sp1.getSslInboundContext();
            AcmeClient acmeClient = acmeSSLContext.getClient();
            String[] hosts = acmeSSLContext.getHosts();

            // ---

            acmeClient.loadDirectory();

            String accountUrl = acmeClient.createAccount();

            OrderAndLocation ol = acmeClient.createOrder(accountUrl, Arrays.asList(hosts));

            assertTrue(ol.getOrder().getAuthorizations().size() > 0);
            for (String authorization : ol.getOrder().getAuthorizations()) {

                Authorization auth = acmeClient.getAuth(accountUrl, authorization);

                assertEquals(AUTHORIZATION_STATUS_PENDING, auth.getStatus());
                String challengeUrl = acmeClient.provision(auth);

                acmeClient.readyForChallenge(accountUrl, challengeUrl);

                long wait = 100;
                while (AUTHORIZATION_STATUS_PENDING.equals(auth.getStatus())) {
                    wait = waitAndCompute(wait);
                    auth = acmeClient.getAuth(accountUrl, authorization);
                }

                assertEquals(AUTHORIZATION_STATUS_VALID, auth.getStatus());
            }

            AcmeKeyPair key = acmeClient.generateCertificateKey();
            acmeClient.getAsse().setKeyPair(hosts, key);


            String csr = acmeClient.generateCSR(hosts, key.getPrivateKey());
            ol = new OrderAndLocation(acmeClient.finalizeOrder(accountUrl, ol.getOrder().getFinalize(), csr), ol.getLocation());

            long wait = 50;
            while (ORDER_STATUS_READY.equals(ol.getOrder().getStatus()) || ORDER_STATUS_PROCESSING.equals(ol.getOrder().getStatus())) {
                wait = waitAndCompute(wait);
                ol = acmeClient.getOrder(accountUrl, ol.getLocation());
            }


            assertEquals(Order.ORDER_STATUS_VALID, ol.getOrder().getStatus());

            acmeClient.getAsse().setCertChain(hosts, acmeClient.downloadCertificate(accountUrl, ol.getOrder().getCertificate()));

            // ---

            while (!acmeSSLContext.isReady()) {
                Thread.sleep(10);
            }

            HttpClient hc = new HttpClient();
            Exchange e = new Request.Builder().get("https://localhost:3051/").buildExchange();
            SSLParser sslParser1 = new SSLParser();
            Trust trust = new Trust();
            Certificate certificate = new Certificate();
            certificate.setContent(sim.getCA().getCertificate());
            trust.setCertificateList(ImmutableList.of(certificate));
            sslParser1.setTrust(trust);
            e.setProperty(Exchange.SSL_CONTEXT, new StaticSSLContext(sslParser1, router.getResolverMap(), router.getBaseLocation()));
            hc.call(e);

            assertEquals(234, e.getResponse().getStatusCode());
        } finally {
            router.stop(); // TODO shutdown router in AfterAll
        }

    }

    private static long waitAndCompute(long wait) throws InterruptedException {
        Thread.sleep(wait);
        if (wait < 500)
            wait *= 2;
        return wait;
    }

}
