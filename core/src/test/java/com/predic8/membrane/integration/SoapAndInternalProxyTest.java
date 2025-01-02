/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration;

import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.config.security.Certificate;
import com.predic8.membrane.core.config.security.Key;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.server.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.http.*;
import org.bouncycastle.asn1.x500.*;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.jce.provider.*;
import org.bouncycastle.openssl.jcajce.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.jcajce.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import javax.security.auth.x500.*;
import java.io.*;
import java.math.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the combination of a soapProxy and internalProxy, using "service:internalProxyName/path/to/the?wsdl".
 * <p>
 * Also activates TLS when retrieving the WSDL.
 */
public class SoapAndInternalProxyTest {

    AtomicBoolean trigger = new AtomicBoolean();

    String key;
    String certificate;

    @Test
    public void testWithoutTLS() throws Exception {
        HttpRouter router = new HttpRouter();
        router.setHotDeploy(false);
        router.setRules(Lists.newArrayList(createSoapProxy(), createInternalProxy(false)));
        router.start();

        runCheck(false);

        router.shutdown();
    }

    @Test
    public void testWithTLS() throws Exception {
        generateKeyAndCert();

        trigger.set(false);

        HttpRouter router2 = new HttpRouter();
        router2.setHotDeploy(false);
        router2.setRules(Lists.newArrayList(createTLSProxy()));
        router2.start();

        HttpRouter router = new HttpRouter();
        router.setHotDeploy(false);
        router.setRules(Lists.newArrayList(createSoapProxy(), createInternalProxy(true)));
        router.start();

        runCheck(true);

        router.shutdown();

        router2.shutdown();
    }

    private void runCheck(boolean checkTrigger) throws Exception {
        try(HttpClient hc = new HttpClient()) {
            Response r1 = hc.call(new Request.Builder().get("http://localhost:3047/b?wsdl").buildExchange()).getResponse();
            //r1.write(System.out, true);
            assertTrue(r1.getBodyAsStringDecoded().contains("\"https://a.b.local/b\""));

            if (checkTrigger)
                assertTrue(trigger.getAndSet(false));

            Response r2 = hc.call(new Request.Builder().post("http://localhost:3047/b").body(getBody()).buildExchange()).getResponse();
            //r2.write(System.out,true);

            assertTrue(r2.getBodyAsStringDecoded().contains("population"));

            if (checkTrigger)
                assertTrue(trigger.getAndSet(false));
        }
    }

    private @NotNull String getBody() throws IOException {
        return new String(requireNonNull(this.getClass().getResourceAsStream("/get-city.xml")).readAllBytes(), UTF_8);
    }

    private void generateKeyAndCert() throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, IOException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keypair = keyGen.generateKeyPair();
        PublicKey publicKey = keypair.getPublic();
        PrivateKey privateKey = keypair.getPrivate();

        String signerAlgo = "SHA256withRSA";
        ContentSigner signGen = new JcaContentSignerBuilder(signerAlgo).build(privateKey);

        X500Name subject = X500Name.getInstance(new X500Principal("CN=predic8 GmbH, OU=Demo, O=Demo, C=DE").getEncoded());
        SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                subject,
                new BigInteger("1"),
                new Date(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000),
                subject,
                keyInfo);

        List<GeneralName> namesList = new ArrayList<>();
        namesList.add(new GeneralName(2, "localhost"));
        GeneralNames subjectAltNames = new GeneralNames(namesList.toArray(new GeneralName [] {}));
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certBuilder.build(signGen));

        StringWriter sw = new StringWriter();
        try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            pw.writeObject(cert);
        }
        certificate = sw.toString();

        sw = new StringWriter();
        JcaPEMWriter writer2 = new JcaPEMWriter(sw);
        writer2.writeObject(privateKey);
        writer2.close();
        key = sw.toString();
    }

    private ServiceProxy createTLSProxy() throws Exception {
        ServiceProxy sp = new ServiceProxy();
        sp.setPort(3048);
        SSLParser ssl = new SSLParser();
        Key k = new Key();
        Key.Private private_ = new Key.Private();
        private_.setContent(key);
        Certificate c = new Certificate();
        c.setContent(certificate);
        k.setCertificates(Lists.newArrayList(c));
        k.setPrivate(private_);
        ssl.setKey(k);
        sp.setSslInboundParser(ssl);

        AbstractInterceptor triggerInterceptor = new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                trigger.set(true);
                return super.handleRequest(exc);
            }
        };

        WSDLInterceptor wsdlInterceptor = new WSDLInterceptor();
        wsdlInterceptor.setProtocol("https");
        sp.setInterceptors(Lists.newArrayList(triggerInterceptor, wsdlInterceptor));

        ServiceProxy rule = new ServiceProxy(new ServiceProxyKey(9500), null, 0);
        rule.getInterceptors().add(new SampleSoapServiceInterceptor());
        Router router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(rule);
        router.init();

        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        target.setHost("localhost");
        target.setPort(9500);
        sp.setTarget(target);
        return sp;
    }

    private Proxy createInternalProxy(boolean useTLS) throws Exception {
        APIProxy internalProxy = new APIProxy();
        internalProxy.setName("int");
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        if (useTLS) {
            target.setHost("localhost");
            target.setPort(3048);
            SSLParser ssl = new SSLParser();
            Trust trust = new Trust();
            Certificate ca = new Certificate();
            ca.setContent(certificate);
            trust.setCertificateList(Lists.newArrayList(ca));
            ssl.setTrust(trust);
            target.setSslParser(ssl);
        } else {
            ServiceProxy rule = new ServiceProxy(new ServiceProxyKey(9501), null, 0);
            rule.getInterceptors().add(new SampleSoapServiceInterceptor());
            Router router = new HttpRouter();
            router.getRuleManager().addProxyAndOpenPortIfNew(rule);
            router.init();
            target.setHost("localhost");
            target.setPort(9501);
        }
        internalProxy.setTarget(target);
        return internalProxy;
    }

    private Proxy createSoapProxy() {
        SOAPProxy soapProxy = new SOAPProxy();
        soapProxy.setPort(3047);
        soapProxy.setWsdl("service:int/?wsdl");
        Path path = new Path();
        path.setValue("/b");
        soapProxy.setPath(path);

        WSDLInterceptor e = new WSDLInterceptor();
        e.setPort("443");
        e.setProtocol("https");
        e.setHost("a.b.local");
        soapProxy.getInterceptors().add(e);

        soapProxy.getInterceptors().add(new WSDLPublisherInterceptor());

        return soapProxy;
    }
}
