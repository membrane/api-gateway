/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.as2;

import com.predic8.membrane.core.config.security.Certificate;
import com.predic8.membrane.core.config.security.Key;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.ConfigurationException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.http.Response.internalServerError;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static org.junit.jupiter.api.Assertions.*;

class As2ReceiverInterceptorTest {

    static final String SERVER_KEY = "classpath:/ca/server-key.pem";
    static final String SERVER_CERT = "classpath:/ca/server.pem";
    static final String CLIENT_CERT = "classpath:/ca/client.pem";

    static class RecordingReceiver implements As2ReceiverExtension {
        As2ReceiverSettings settings;
        Outcome outcome = CONTINUE;
        IOException failure;
        final List<Boolean> responses = new ArrayList<>();

        @Override
        public void init(final As2ReceiverSettings settings) {
            this.settings = settings;
        }

        @Override
        public Outcome receive(final Exchange exchange) throws IOException {
            if (failure != null)
                throw failure;
            return outcome;
        }

        @Override
        public void respond(final Exchange exchange, final boolean processed) throws IOException {
            if (failure != null)
                throw failure;
            responses.add(processed);
            exchange.setResponse(ok("MDN processed=" + processed).build());
        }
    }

    @TempDir
    Path tempDir;

    private final RecordingReceiver receiver = new RecordingReceiver();

    private As2ReceiverInterceptor interceptor() {
        final var i = new As2ReceiverInterceptor() {
            @Override
            As2ReceiverExtension createReceiver() {
                return receiver;
            }
        };
        i.setAs2Id("MEMBRANE");
        i.setKey(key(location(SERVER_KEY), SERVER_CERT));
        i.setPartners(new ArrayList<>(List.of(partner("ACME", CLIENT_CERT))));
        return i;
    }

    static Key.Private location(final String location) {
        final var p = new Key.Private();
        p.setLocation(location);
        return p;
    }

    static Key key(final Key.Private privateKey, final String certificateLocation) {
        final var key = new Key();
        key.setPrivate(privateKey);
        key.setCertificates(List.of(certificate(certificateLocation)));
        return key;
    }

    static Certificate certificate(final String location) {
        final var c = new Certificate();
        c.setLocation(location);
        return c;
    }

    static As2Partner partner(final String as2Id, final String certificateLocation) {
        final var p = new As2Partner();
        p.setAs2Id(as2Id);
        p.setCertificate(certificate(certificateLocation));
        return p;
    }

    static KeyStore keyStore(final String alias) {
        final var ks = new KeyStore();
        ks.setLocation("classpath:/alias-keystore.p12");
        ks.setPassword("secret");
        ks.setKeyAlias(alias);
        return ks;
    }

    private static ConfigurationException initFails(final As2ReceiverInterceptor i) {
        return assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
    }

    @Test
    void missingExtensionFailsOnStartup() {
        final var i = new As2ReceiverInterceptor();
        i.setAs2Id("MEMBRANE");
        i.setKey(key(location(SERVER_KEY), SERVER_CERT));
        i.setPartners(List.of(partner("ACME", CLIENT_CERT)));
        assertTrue(initFails(i).getMessage().contains("Membrane AS2 Extension"));
    }

    @Test
    void passesSettingsToExtension() {
        final var i = interceptor();
        i.setRequireSigned(false);
        i.setRequireEncrypted(true);
        i.setMaxPayloadSize(1000);
        i.init(new DummyTestRouter());

        final var s = receiver.settings;
        assertEquals("MEMBRANE", s.as2Id());
        assertEquals("RSA", s.privateKey().getAlgorithm());
        assertTrue(s.certificate().getSubjectX500Principal().getName().contains("CN=server"));
        assertEquals(List.of("ACME"), List.copyOf(s.partners().keySet()));
        assertTrue(s.partners().get("ACME").getSubjectX500Principal().getName().contains("CN=client"));
        assertFalse(s.requireSigned());
        assertTrue(s.requireEncrypted());
        assertEquals(1000, s.maxPayloadSize());
    }

    @Test
    void defaults() {
        interceptor().init(new DummyTestRouter());
        assertTrue(receiver.settings.requireSigned());
        assertFalse(receiver.settings.requireEncrypted());
        assertEquals(100L * 1024 * 1024, receiver.settings.maxPayloadSize());
    }

    @Test
    void settingsToStringOmitsPrivateKey() {
        interceptor().init(new DummyTestRouter());
        assertFalse(receiver.settings.toString().contains(receiver.settings.privateKey().toString()));
        assertTrue(receiver.settings.toString().contains("ACME"));
    }

    @Test
    void keyFromKeystore() {
        final var i = interceptor();
        i.setKey(null);
        i.setKeyStore(keyStore("key1"));
        i.init(new DummyTestRouter());
        assertTrue(receiver.settings.certificate().getSubjectX500Principal().getName().contains("CN=key1"));
    }

    @Test
    void unknownKeystoreAlias() {
        final var i = interceptor();
        i.setKey(null);
        i.setKeyStore(keyStore("nobody"));
        assertTrue(initFails(i).getMessage().contains("nobody"));
    }

    @Test
    void needsKeystoreOrKey() {
        final var i = interceptor();
        i.setKey(null);
        assertTrue(initFails(i).getMessage().contains("either a keystore or a key"));
    }

    @Test
    void rejectsKeystoreAndKey() {
        final var i = interceptor();
        i.setKeyStore(keyStore("key1"));
        assertTrue(initFails(i).getMessage().contains("either a keystore or a key"));
    }

    @Test
    void keyNeedsCertificate() {
        final var i = interceptor();
        final var key = new Key();
        key.setPrivate(location(SERVER_KEY));
        i.setKey(key);
        assertTrue(initFails(i).getMessage().contains("needs a certificate"));
    }

    @Test
    void certificateMustBelongToKey() {
        final var i = interceptor();
        i.setKey(key(location(SERVER_KEY), CLIENT_CERT));
        assertTrue(initFails(i).getMessage().contains("does not belong to its private key"));
    }

    @Test
    void needsAs2Id() {
        final var i = interceptor();
        i.setAs2Id(null);
        assertTrue(initFails(i).getMessage().contains("as2Id of as2Receiver is required"));
    }

    @Test
    void as2IdAtMost128Characters() {
        final var i = interceptor();
        i.setAs2Id("A".repeat(128));
        i.init(new DummyTestRouter());
        i.setAs2Id("A".repeat(129));
        assertTrue(initFails(i).getMessage().contains("128"));
    }

    @Test
    void as2IdMustBePrintableAscii() {
        final var i = interceptor();
        i.setAs2Id("MÜNCHEN");
        assertTrue(initFails(i).getMessage().contains("printable ASCII"));
    }

    @Test
    void as2IdWithInnerSpaceIsAllowed() {
        final var i = interceptor();
        i.setAs2Id("MEMBRANE GATEWAY");
        i.init(new DummyTestRouter());
        assertEquals("MEMBRANE GATEWAY", receiver.settings.as2Id());
    }

    @Test
    void as2IdMustNotStartOrEndWithSpace() {
        final var i = interceptor();
        i.setAs2Id("MEMBRANE ");
        assertTrue(initFails(i).getMessage().contains("space"));
    }

    @Test
    void needsPartner() {
        final var i = interceptor();
        i.setPartners(new ArrayList<>());
        assertTrue(initFails(i).getMessage().contains("at least one partner"));
    }

    @Test
    void partnerNeedsCertificate() {
        final var i = interceptor();
        final var partner = new As2Partner();
        partner.setAs2Id("ACME");
        i.setPartners(List.of(partner));
        assertTrue(initFails(i).getMessage().contains("Partner ACME of as2Receiver needs a certificate"));
    }

    @Test
    void partnerNeedsValidAs2Id() {
        final var i = interceptor();
        i.setPartners(List.of(partner(null, CLIENT_CERT)));
        assertTrue(initFails(i).getMessage().contains("as2Id of a partner of as2Receiver is required"));
    }

    @Test
    void rejectsDuplicatePartners() {
        final var i = interceptor();
        i.setPartners(List.of(partner("ACME", CLIENT_CERT), partner("ACME", SERVER_CERT)));
        assertTrue(initFails(i).getMessage().contains("more than one partner with the AS2 ID ACME"));
    }

    @Test
    void unreadablePartnerCertificate() {
        final var i = interceptor();
        i.setPartners(List.of(partner("ACME", "classpath:/empty.pem")));
        assertTrue(initFails(i).getMessage().contains("certificate of partner ACME"));
    }

    @Test
    void maxPayloadSizeMustBePositive() {
        final var i = interceptor();
        i.setMaxPayloadSize(0);
        assertTrue(initFails(i).getMessage().contains("maxPayloadSize"));
    }

    @Test
    void ecKeyCannotDecrypt() throws Exception {
        final var i = interceptor();
        i.setKey(generatedKey("EC", Instant.now().plus(Duration.ofDays(1))));
        i.setRequireEncrypted(true);
        assertTrue(initFails(i).getMessage().contains("Decryption needs an RSA key"));
    }

    @Test
    void ecKeyWithoutEncryptionIsAccepted() throws Exception {
        final var events = captureLog(() -> {
            final var i = interceptor();
            i.setKey(generatedKey("EC", Instant.now().plus(Duration.ofDays(1))));
            i.init(new DummyTestRouter());
        });
        assertNotNull(receiver.settings);
        assertTrue(hasWarning(events, "is an EC key"));
    }

    @Test
    void warnsAboutUnsignedMessages() throws Exception {
        final var events = captureLog(() -> {
            final var i = interceptor();
            i.setRequireSigned(false);
            i.init(new DummyTestRouter());
        });
        assertTrue(hasWarning(events, "accepts unsigned messages"));
    }

    @Test
    void warnsAboutExpiredCertificate() throws Exception {
        final var events = captureLog(() -> {
            final var i = interceptor();
            i.setKey(generatedKey("RSA", Instant.now().minus(Duration.ofDays(1))));
            i.init(new DummyTestRouter());
        });
        assertTrue(hasWarning(events, "The certificate of as2Receiver MEMBRANE expired"));
    }

    @Test
    void requestOutcomeComesFromExtension() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        for (final var outcome : Outcome.values()) {
            receiver.outcome = outcome;
            assertEquals(outcome, i.handleRequest(exchange()));
        }
    }

    @Test
    void successfulBackendResponseIsProcessed() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        final var exc = exchange();
        exc.setResponse(ok().build());
        assertEquals(CONTINUE, i.handleResponse(exc));
        assertEquals(List.of(true), receiver.responses);
    }

    @Test
    void failedBackendResponseIsNotProcessed() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        final var exc = exchange();
        exc.setResponse(internalServerError().build());
        assertEquals(CONTINUE, i.handleResponse(exc));
        assertEquals(List.of(false), receiver.responses);
    }

    @Test
    void abortIsNotProcessed() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        i.handleAbort(exchange());
        assertEquals(List.of(false), receiver.responses);
    }

    @Test
    void extensionFailureIsAnswered500() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        receiver.failure = new IOException("broken");
        final var exc = exchange();
        assertEquals(ABORT, i.handleRequest(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    /**
     * FlowController answers it with 400.
     */
    @Test
    void unreadableBodyIsLeftToFlowController() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        receiver.failure = new IOException(new ReadingBodyException("invalid gzip"));
        assertThrows(ReadingBodyException.class, () -> i.handleRequest(exchange()));
    }

    /**
     * The @yaml example of the reference documentation.
     */
    @Test
    void yamlDocumentationExample() throws Exception {
        final var i = loadReceiver("""
                api:
                  port: 2000
                  path:
                    uri: /as2
                  flow:
                    - as2Receiver:
                        as2Id: MEMBRANE
                        keystore:
                          location: membrane.p12
                          password: secret
                          keyAlias: membrane
                        partners:
                          - as2Id: ACME
                            certificate:
                              location: acme.pem
                        requireEncrypted: true
                  target:
                    url: http://erp.example.com/inbound
                """);
        assertEquals("MEMBRANE", i.getAs2Id());
        assertEquals("membrane.p12", i.getKeyStore().getLocation());
        assertEquals("membrane", i.getKeyStore().getKeyAlias());
        assertEquals(1, i.getPartners().size());
        assertEquals("ACME", i.getPartners().getFirst().getAs2Id());
        assertEquals("acme.pem", i.getPartners().getFirst().getCertificate().getLocation());
        assertTrue(i.isRequireEncrypted());
        assertTrue(i.isRequireSigned());
    }

    @Test
    void yamlPemKey() throws Exception {
        final var i = loadReceiver("""
                api:
                  port: 2000
                  flow:
                    - as2Receiver:
                        as2Id: MEMBRANE
                        key:
                          private:
                            location: membrane-key.pem
                          certificates:
                            - location: membrane.pem
                        partners:
                          - as2Id: ACME
                            certificate:
                              location: acme.pem
                          - as2Id: GLOBEX
                            certificate:
                              location: globex.pem
                        requireSigned: false
                        maxPayloadSize: 10485760
                """);
        assertEquals("membrane-key.pem", i.getKey().getPrivate().getLocation());
        assertEquals("membrane.pem", i.getKey().getCertificates().getFirst().getLocation());
        assertEquals(List.of("ACME", "GLOBEX"), i.getPartners().stream().map(As2Partner::getAs2Id).toList());
        assertFalse(i.isRequireSigned());
        assertEquals(10485760, i.getMaxPayloadSize());
    }

    @Test
    void routerSendsProcessedMdnForBackend200() throws Exception {
        assertEquals("MDN processed=true", callThroughRouter(200));
    }

    @Test
    void routerSendsErrorMdnForBackend500() throws Exception {
        assertEquals("MDN processed=false", callThroughRouter(500));
    }

    /**
     * The response set in handleAbort() is the one the client gets.
     */
    @Test
    void routerSendsErrorMdnForUnreachableBackend() throws Exception {
        assertEquals("MDN processed=false", callThroughRouter(0));
    }

    /**
     * @param backendStatus status of the backend, 0 for a backend that is not running
     * @return body of the response the client gets
     */
    private String callThroughRouter(final int backendStatus) throws Exception {
        final var gatewayPort = freePort();
        final var backendPort = freePort();
        final var router = new TestRouter();
        try {
            final var gateway = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", gatewayPort), "localhost", backendPort);
            gateway.getFlow().add(interceptor());
            router.add(gateway);
            if (backendStatus != 0) {
                final var backend = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", backendPort), "", -1);
                backend.getFlow().add(new ReturnInterceptor() {{
                    setStatus(backendStatus);
                }});
                router.add(backend);
            }
            router.start();
            try (var client = new HttpClient()) {
                final var exc = post("http://localhost:%d/as2".formatted(gatewayPort)).body("AS2 message").buildExchange();
                client.call(exc);
                return exc.getResponse().getBodyAsStringDecoded();
            }
        } finally {
            router.stop();
        }
    }

    private As2ReceiverInterceptor loadReceiver(final String yaml) throws Exception {
        final var config = tempDir.resolve("apis.yaml");
        Files.writeString(config, yaml);
        final var router = new DefaultRouter();
        try {
            loadIntoRouter(router, config.toString());
            return assertInstanceOf(As2ReceiverInterceptor.class, router.getRuleManager().getRules().getFirst().getFlow().getFirst());
        } finally {
            router.stop();
        }
    }

    private static Exchange exchange() throws Exception {
        return post("/as2").body("AS2 message").buildExchange();
    }

    /**
     * Self-signed certificate with the given end of validity, as PEM key.
     */
    static Key generatedKey(final String algorithm, final Instant notAfter) throws Exception {
        final var generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(algorithm.equals("EC") ? 256 : 2048);
        final KeyPair pair = generator.generateKeyPair();
        final var name = new X500Name("CN=as2.example.com");
        final var holder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date.from(notAfter.minus(Duration.ofDays(30))), Date.from(notAfter), name, pair.getPublic())
                .build(new JcaContentSignerBuilder(algorithm.equals("EC") ? "SHA256withECDSA" : "SHA256withRSA").build(pair.getPrivate()));
        final var privateKey = new Key.Private();
        // PKCS#8: PEMSupport cannot read a SEC1 EC key without the public key, as JcaPEMWriter writes it
        privateKey.setContent(pem(new JcaPKCS8Generator(pair.getPrivate(), null)));
        final var certificate = new Certificate();
        certificate.setContent(pem(holder));
        final var key = new Key();
        key.setPrivate(privateKey);
        key.setCertificates(List.of(certificate));
        return key;
    }

    static String pem(final Object object) throws IOException {
        final var out = new StringWriter();
        try (var writer = new JcaPEMWriter(out)) {
            writer.writeObject(object);
        }
        return out.toString();
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static List<LogEvent> captureLog(final ThrowingRunnable action) throws Exception {
        return captureLog(As2ReceiverInterceptor.class, action);
    }

    /**
     * @param loggerClass Class whose logger is captured
     */
    static List<LogEvent> captureLog(final Class<?> loggerClass, final ThrowingRunnable action) throws Exception {
        final var events = new CopyOnWriteArrayList<LogEvent>();
        final var appender = new AbstractAppender("As2ReceiverInterceptorTest", null, null, false, null) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        final var interceptorLog = (Logger) LogManager.getLogger(loggerClass);
        interceptorLog.addAppender(appender);
        try {
            action.run();
            return events;
        } finally {
            interceptorLog.removeAppender(appender);
        }
    }

    static boolean hasWarning(final List<LogEvent> events, final String text) {
        return events.stream().anyMatch(e -> e.getLevel() == Level.WARN && e.getMessage().getFormattedMessage().contains(text));
    }
}
