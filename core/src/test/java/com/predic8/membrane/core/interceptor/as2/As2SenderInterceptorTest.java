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

import com.predic8.membrane.core.config.security.Key;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.Compression;
import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.Encryption;
import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.Mdn;
import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.SignatureDigest;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.ConfigurationException;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.as2.As2ReceiverInterceptorTest.*;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static org.junit.jupiter.api.Assertions.*;

class As2SenderInterceptorTest {

    static class RecordingSender implements As2SenderExtension {
        As2SenderSettings settings;
        Outcome outcome = CONTINUE;
        IOException failure;
        final List<Integer> responses = new ArrayList<>();
        int aborts;

        @Override
        public void init(final As2SenderSettings settings) {
            this.settings = settings;
        }

        @Override
        public Outcome send(final Exchange exchange) throws IOException {
            if (failure != null)
                throw failure;
            return outcome;
        }

        @Override
        public void handleResponse(final Exchange exchange) throws IOException {
            if (failure != null)
                throw failure;
            final var status = exchange.getResponse().getStatusCode();
            responses.add(status);
            exchange.setResponse(ok("checked " + status).build());
        }

        @Override
        public void handleAbort(final Exchange exchange) {
            aborts++;
            if (exchange.getResponse() != null)
                exchange.getResponse().getHeader().setValue("AS2-Message-ID", "<aborted>");
        }
    }

    @TempDir
    Path tempDir;

    private final RecordingSender sender = new RecordingSender();

    private As2SenderInterceptor interceptor() {
        final var i = new As2SenderInterceptor() {
            @Override
            As2SenderExtension createSender() {
                return sender;
            }
        };
        i.setAs2Id("MEMBRANE");
        i.setKey(key(location(SERVER_KEY), SERVER_CERT));
        i.setPartner(partner("ACME", CLIENT_CERT));
        return i;
    }

    /**
     * Partner ACME with the certificate of a generated key.
     */
    private static As2Partner partnerWith(final Key generated) {
        return As2ReceiverInterceptorTest.partnerWith("ACME", generated);
    }

    private static ConfigurationException initFails(final As2SenderInterceptor i) {
        return assertThrows(ConfigurationException.class, () -> i.init(new DummyTestRouter()));
    }

    private static List<LogEvent> captureLog(final ThrowingRunnable action) throws Exception {
        return As2ReceiverInterceptorTest.captureLog(As2SenderInterceptor.class, action);
    }

    @Test
    void missingExtensionFailsOnStartup() {
        final var i = new As2SenderInterceptor();
        i.setAs2Id("MEMBRANE");
        i.setKey(key(location(SERVER_KEY), SERVER_CERT));
        i.setPartner(partner("ACME", CLIENT_CERT));
        assertTrue(initFails(i).getMessage().contains("Membrane AS2 Extension"));
    }

    @Test
    void passesSettingsToExtension() {
        final var i = interceptor();
        i.setSignatureDigest(SignatureDigest.SHA512);
        i.setEncryption(Encryption.AES128_CBC);
        i.setCompression(Compression.AFTER_SIGNING);
        i.setMdn(Mdn.UNSIGNED);
        i.setSubject("Orders");
        i.setDispositionNotificationTo("as2@example.com");
        i.setMessageIdDomain("example.com");
        i.setMaxPayloadSize(1000);
        i.setInsecureAllowExpiredCertificates(true);
        i.init(new DummyTestRouter());

        final var s = sender.settings;
        assertEquals("MEMBRANE", s.as2Id());
        assertEquals("RSA", s.privateKey().getAlgorithm());
        assertTrue(s.certificate().getSubjectX500Principal().getName().contains("CN=server"));
        assertEquals("ACME", s.partnerAs2Id());
        assertTrue(s.partnerCertificate().getSubjectX500Principal().getName().contains("CN=client"));
        assertEquals(SignatureDigest.SHA512, s.signatureDigest());
        assertEquals(Encryption.AES128_CBC, s.encryption());
        assertEquals(Compression.AFTER_SIGNING, s.compression());
        assertEquals(Mdn.UNSIGNED, s.mdn());
        assertEquals("Orders", s.subject());
        assertEquals("as2@example.com", s.dispositionNotificationTo());
        assertEquals("example.com", s.messageIdDomain());
        assertEquals(1000, s.maxPayloadSize());
        assertTrue(s.insecureAllowExpiredCertificates());
    }

    @Test
    void defaults() {
        interceptor().init(new DummyTestRouter());
        final var s = sender.settings;
        assertEquals(SignatureDigest.SHA256, s.signatureDigest());
        assertEquals(Encryption.AES256_CBC, s.encryption());
        assertEquals(Compression.NONE, s.compression());
        assertEquals(Mdn.SIGNED, s.mdn());
        assertEquals("AS2 Message", s.subject());
        assertEquals("MEMBRANE", s.dispositionNotificationTo());
        assertEquals("as2.local", s.messageIdDomain());
        assertEquals(100L * 1024 * 1024, s.maxPayloadSize());
        assertFalse(s.insecureAllowExpiredCertificates());
    }

    @Test
    void settingsToStringOmitsPrivateKey() {
        interceptor().init(new DummyTestRouter());
        assertFalse(sender.settings.toString().contains(sender.settings.privateKey().toString()));
        assertTrue(sender.settings.toString().contains("ACME"));
    }

    @Test
    void keyFromKeystore() {
        final var i = interceptor();
        i.setKey(null);
        i.setKeyStore(keyStore("key1"));
        i.init(new DummyTestRouter());
        assertTrue(sender.settings.certificate().getSubjectX500Principal().getName().contains("CN=key1"));
    }

    @Test
    void needsKeystoreOrKey() {
        final var i = interceptor();
        i.setKey(null);
        assertTrue(initFails(i).getMessage().contains("as2Sender needs either a keystore or a key"));
    }

    @Test
    void certificateMustBelongToKey() {
        final var i = interceptor();
        i.setKey(key(location(SERVER_KEY), CLIENT_CERT));
        assertTrue(initFails(i).getMessage().contains("The certificate of as2Sender does not belong to its private key"));
    }

    @Test
    void needsAs2Id() {
        final var i = interceptor();
        i.setAs2Id(null);
        assertTrue(initFails(i).getMessage().contains("as2Id of as2Sender is required"));
    }

    @Test
    void as2IdMustBePrintableAscii() {
        final var i = interceptor();
        i.setAs2Id("MÜNCHEN");
        assertTrue(initFails(i).getMessage().contains("printable ASCII"));
    }

    @Test
    void needsPartner() {
        final var i = interceptor();
        i.setPartner(null);
        assertTrue(initFails(i).getMessage().contains("as2Sender needs a partner"));
    }

    @Test
    void partnerNeedsCertificate() {
        final var i = interceptor();
        final var partner = new As2Partner();
        partner.setAs2Id("ACME");
        i.setPartner(partner);
        assertTrue(initFails(i).getMessage().contains("Partner ACME of as2Sender needs a certificate"));
    }

    @Test
    void partnerNeedsValidAs2Id() {
        final var i = interceptor();
        i.setPartner(partner("ACME ", CLIENT_CERT));
        assertTrue(initFails(i).getMessage().contains("as2Id of a partner of as2Sender must not start or end with a space"));
    }

    @Test
    void unreadablePartnerCertificate() {
        final var i = interceptor();
        i.setPartner(partner("ACME", "classpath:/empty.pem"));
        assertTrue(initFails(i).getMessage().contains("certificate of partner ACME of as2Sender"));
    }

    @Test
    void maxPayloadSizeMustBePositive() {
        final var i = interceptor();
        i.setMaxPayloadSize(0);
        assertTrue(initFails(i).getMessage().contains("maxPayloadSize"));
    }

    @Test
    void subjectMustBePrintableAscii() {
        final var i = interceptor();
        i.setSubject("Orders\r\nX-Injected: 1");
        assertTrue(initFails(i).getMessage().contains("subject of as2Sender must consist of printable ASCII"));
    }

    @Test
    void subjectMustNotBeEmpty() {
        final var i = interceptor();
        i.setSubject("");
        assertTrue(initFails(i).getMessage().contains("subject of as2Sender must not be empty"));
    }

    @Test
    void dispositionNotificationToMustBePrintableAscii() {
        final var i = interceptor();
        i.setDispositionNotificationTo("as2@exämple.com");
        assertTrue(initFails(i).getMessage().contains("dispositionNotificationTo"));
    }

    @Test
    void messageIdDomainMustBeDomain() {
        final var i = interceptor();
        i.setMessageIdDomain("a>b");
        assertTrue(initFails(i).getMessage().contains("messageIdDomain of as2Sender must be a domain name"));
    }

    @Test
    void encryptionNeedsRsaCertificateOfPartner() throws Exception {
        final var i = interceptor();
        i.setPartner(partnerWith(generatedKey("EC", Instant.now().plus(Duration.ofDays(1)))));
        assertTrue(initFails(i).getMessage().contains("Encryption needs an RSA key"));
    }

    @Test
    void ecPartnerWithoutEncryptionIsAcceptedWithWarning() throws Exception {
        final var events = captureLog(() -> {
            final var i = interceptor();
            i.setPartner(partnerWith(generatedKey("EC", Instant.now().plus(Duration.ofDays(1)))));
            i.setEncryption(Encryption.NONE);
            i.init(new DummyTestRouter());
        });
        assertEquals(Encryption.NONE, sender.settings.encryption());
        assertTrue(hasWarning(events, "sends unencrypted messages to ACME"));
    }

    /**
     * Signing works with EC keys.
     */
    @Test
    void ecOwnKeyIsAccepted() throws Exception {
        final var i = interceptor();
        i.setKey(generatedKey("EC", Instant.now().plus(Duration.ofDays(1))));
        i.init(new DummyTestRouter());
        // Bouncy Castle, which parses the PEM key, calls EC keys ECDSA
        assertTrue(List.of("EC", "ECDSA").contains(sender.settings.privateKey().getAlgorithm()));
    }

    @Test
    void rejectsExpiredPartnerCertificate() throws Exception {
        final var i = interceptor();
        i.setPartner(partnerWith(generatedKey("RSA", Instant.now().minus(Duration.ofDays(1)))));
        final var message = initFails(i).getMessage();
        assertTrue(message.contains("The certificate of partner ACME expired on"));
        assertTrue(message.contains("insecureAllowExpiredCertificates of as2Sender"));
    }

    @Test
    void rejectsExpiredOwnCertificate() throws Exception {
        final var i = interceptor();
        i.setKey(generatedKey("RSA", Instant.now().minus(Duration.ofDays(1))));
        assertTrue(initFails(i).getMessage().contains("The certificate of as2Sender MEMBRANE expired on"));
    }

    @Test
    void rejectsOwnCertificateNotValidYet() throws Exception {
        final var i = interceptor();
        // valid from now + 30 days
        i.setKey(generatedKey("RSA", Instant.now().plus(Duration.ofDays(60))));
        assertTrue(initFails(i).getMessage().contains("The certificate of as2Sender MEMBRANE is not valid before"));
    }

    @Test
    void expiredCertificatesAllowedWithWarning() throws Exception {
        final var events = captureLog(() -> {
            final var i = interceptor();
            i.setKey(generatedKey("RSA", Instant.now().minus(Duration.ofDays(1))));
            i.setPartner(partnerWith(generatedKey("RSA", Instant.now().minus(Duration.ofDays(1)))));
            i.setInsecureAllowExpiredCertificates(true);
            i.init(new DummyTestRouter());
        });
        assertTrue(sender.settings.insecureAllowExpiredCertificates());
        assertTrue(hasWarning(events, "as2Sender MEMBRANE accepts expired certificates"));
        assertTrue(hasWarning(events, "The certificate of as2Sender MEMBRANE expired on"));
        assertTrue(hasWarning(events, "The certificate of partner ACME expired on"));
    }

    @Test
    void requestOutcomeComesFromExtension() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        for (final var outcome : Outcome.values()) {
            sender.outcome = outcome;
            assertEquals(outcome, i.handleRequest(exchange()));
        }
    }

    @Test
    void responseGoesToExtension() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        final var exc = exchange();
        exc.setResponse(ok().build());
        assertEquals(CONTINUE, i.handleResponse(exc));
        assertEquals(List.of(200), sender.responses);
        assertEquals("checked 200", exc.getResponse().getBodyAsStringDecoded());
    }

    @Test
    void extensionFailureIsAnswered500() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        sender.failure = new IOException("broken");
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
        sender.failure = new IOException(new ReadingBodyException("invalid gzip"));
        assertThrows(ReadingBodyException.class, () -> i.handleRequest(exchange()));
    }

    /**
     * The @yaml example of the reference documentation.
     */
    @Test
    void yamlDocumentationExample() throws Exception {
        final var i = loadSender("""
                api:
                  port: 2001
                  path:
                    uri: /acme
                  flow:
                    - as2Sender:
                        as2Id: MEMBRANE
                        keystore:
                          location: membrane.p12
                          password: secret
                          keyAlias: membrane
                        partner:
                          as2Id: ACME
                          certificate:
                            location: acme.pem
                        compression: before_signing
                  target:
                    url: https://as2.acme.example.com/as2
                """);
        assertEquals("MEMBRANE", i.getAs2Id());
        assertEquals("membrane.p12", i.getKeyStore().getLocation());
        assertEquals("membrane", i.getKeyStore().getKeyAlias());
        assertEquals("ACME", i.getPartner().getAs2Id());
        assertEquals("acme.pem", i.getPartner().getCertificate().getLocation());
        assertEquals(Compression.BEFORE_SIGNING, i.getCompression());
        assertEquals(Encryption.AES256_CBC, i.getEncryption());
    }

    @Test
    void yamlAllAttributes() throws Exception {
        final var i = loadSender("""
                api:
                  port: 2001
                  flow:
                    - as2Sender:
                        as2Id: MEMBRANE
                        key:
                          private:
                            location: membrane-key.pem
                          certificates:
                            - location: membrane.pem
                        partner:
                          as2Id: ACME
                          certificate:
                            location: acme.pem
                        signatureDigest: sha384
                        encryption: triple_des
                        compression: after_signing
                        mdn: none
                        subject: Orders
                        dispositionNotificationTo: as2@example.com
                        messageIdDomain: example.com
                        maxPayloadSize: 10485760
                        insecureAllowExpiredCertificates: true
                  target:
                    url: https://as2.acme.example.com/as2
                """);
        assertEquals("membrane-key.pem", i.getKey().getPrivate().getLocation());
        assertEquals(SignatureDigest.SHA384, i.getSignatureDigest());
        assertEquals(Encryption.TRIPLE_DES, i.getEncryption());
        assertEquals(Compression.AFTER_SIGNING, i.getCompression());
        assertEquals(Mdn.NONE, i.getMdn());
        assertEquals("Orders", i.getSubject());
        assertEquals("as2@example.com", i.getDispositionNotificationTo());
        assertEquals("example.com", i.getMessageIdDomain());
        assertEquals(10485760, i.getMaxPayloadSize());
        assertTrue(i.isInsecureAllowExpiredCertificates());
    }

    @Test
    void abortGoesToExtension() throws Exception {
        final var i = interceptor();
        i.init(new DummyTestRouter());
        i.handleAbort(exchange());
        assertEquals(1, sender.aborts);
    }

    @Test
    void routerPassesPartnerResponseToExtension() throws Exception {
        assertEquals("checked 500", callThroughRouter(500).getBodyAsStringDecoded());
        assertEquals(List.of(500), sender.responses);
        assertEquals(0, sender.aborts);
    }

    /**
     * Membrane's own error reaches the client, with what the extension added in handleAbort().
     */
    @Test
    void routerLeavesUnreachablePartnerToMembrane() throws Exception {
        final var response = callThroughRouter(0);
        assertEquals(List.of(), sender.responses);
        assertEquals(1, sender.aborts);
        assertTrue(response.getStatusCode() >= 500);
        assertEquals("<aborted>", response.getHeader().getFirstValue("AS2-Message-ID"));
    }

    /**
     * @param partnerStatus status of the partner, 0 for a partner that is not running
     * @return response the client gets
     */
    private Response callThroughRouter(final int partnerStatus) throws Exception {
        final var gatewayPort = freePort();
        final var partnerPort = freePort();
        final var router = new TestRouter();
        try {
            final var gateway = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", gatewayPort), "localhost", partnerPort);
            gateway.getFlow().add(interceptor());
            router.add(gateway);
            if (partnerStatus != 0) {
                final var partner = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", partnerPort), "", -1);
                partner.getFlow().add(new ReturnInterceptor() {{
                    setStatus(partnerStatus);
                }});
                router.add(partner);
            }
            router.start();
            try (var client = new HttpClient()) {
                final var exc = post("http://localhost:%d/acme".formatted(gatewayPort)).body("EDI").buildExchange();
                client.call(exc);
                return exc.getResponse();
            }
        } finally {
            router.stop();
        }
    }

    private As2SenderInterceptor loadSender(final String yaml) throws Exception {
        final var config = tempDir.resolve("apis.yaml");
        Files.writeString(config, yaml);
        final var router = new DefaultRouter();
        try {
            loadIntoRouter(router, config.toString());
            return assertInstanceOf(As2SenderInterceptor.class, router.getRuleManager().getRules().getFirst().getFlow().getFirst());
        } finally {
            router.stop();
        }
    }

    private static Exchange exchange() throws Exception {
        return post("/acme").body("EDI").buildExchange();
    }
}
