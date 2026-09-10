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
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.util.ConfigurationException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The checks on the recipient's certificate, which are configuration-time rather than per-message:
 * a certificate that cannot encrypt is a misconfiguration, not a bad message.
 * <p>
 * Separate from {@link EncryptSecurePartTest} because these need purpose-built certificates -
 * expired, restricted to signing, or not RSA at all - and the shared keystore fixtures declare no
 * {@code keyUsage} extension, so they cannot express any of these cases.
 */
class EncryptRecipientCertificateTest extends AbstractWsSecurityTest {

    private static final String RECIPIENT_ALIAS = "recipient";

    @TempDir
    Path tempDir;

    @Test
    void anExpiredRecipientCertificateIsRejected() throws Exception {
        Instant now = Instant.now();
        assertRejects(recipientStore("RSA", now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(1)), 0),
                "not valid at this time");
    }

    @Test
    void aNotYetValidRecipientCertificateIsRejected() throws Exception {
        Instant now = Instant.now();
        assertRejects(recipientStore("RSA", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)), 0),
                "not valid at this time");
    }

    /** A certificate that declares it may only sign must not be used to encrypt a key for. */
    @Test
    void aRecipientCertificateWithoutKeyEnciphermentIsRejected() throws Exception {
        assertRejects(validRecipientStore("RSA", KeyUsage.digitalSignature), "keyEncipherment");
    }

    @Test
    void aRecipientCertificateDeclaringKeyEnciphermentIsAccepted() throws Exception {
        assertDoesNotThrow(() -> encryptWith(validRecipientStore("RSA", KeyUsage.keyEncipherment)));
    }

    /** dataEncipherment is the other bit that permits encrypting for a certificate. */
    @Test
    void aRecipientCertificateDeclaringDataEnciphermentIsAccepted() throws Exception {
        assertDoesNotThrow(() -> encryptWith(validRecipientStore("RSA", KeyUsage.dataEncipherment)));
    }

    /**
     * An absent keyUsage extension leaves the key unconstrained, so it must not be read as "may not
     * encrypt" - that would reject the many certificates which simply do not declare one, including
     * this repository's own fixtures.
     */
    @Test
    void aRecipientCertificateWithNoKeyUsageExtensionIsAccepted() throws Exception {
        assertDoesNotThrow(() -> encryptWith(validRecipientStore("RSA", 0)));
    }

    /** RSA-OAEP key transport has nothing to do with an EC key. */
    @Test
    void aNonRsaRecipientCertificateIsRejected() throws Exception {
        assertRejects(validRecipientStore("EC", 0), "RSA");
    }

    private void assertRejects(TrustStore store, String expectedInMessage) {
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> encryptWith(store));
        assertTrue(e.getMessage().contains(expectedInMessage),
                () -> "Expected the error to mention \"" + expectedInMessage + "\", but was: " + e.getMessage());
    }

    private void encryptWith(TrustStore store) {
        WsSecurityInterceptor wsSecurity = securing(encrypt(RECIPIENT_ALIAS, encryptedBodyReference()));
        wsSecurity.setTrustStore(store);
        wsSecurity.init(router);
    }

    private TrustStore validRecipientStore(String keyAlgorithm, int keyUsage) throws Exception {
        Instant now = Instant.now();
        return recipientStore(keyAlgorithm, now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)), keyUsage);
    }

    /**
     * A truststore holding one self-signed certificate for {@code recipient}. Written as a
     * {@code trustedCertEntry}, which is what a real truststore holds.
     */
    private TrustStore recipientStore(String keyAlgorithm, Instant notBefore, Instant notAfter, int keyUsage)
            throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(keyAlgorithm);
        generator.initialize("EC".equals(keyAlgorithm) ? 256 : 2048);
        KeyPair keyPair = generator.generateKeyPair();

        X500Name subject = new X500Name("CN=recipient.example.com");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(1), Date.from(notBefore), Date.from(notAfter), subject,
                keyPair.getPublic());
        if (keyUsage != 0) {
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
        }
        String signatureAlgorithm = "EC".equals(keyAlgorithm) ? "SHA256withECDSA" : "SHA256WithRSA";
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate())));

        java.security.KeyStore store = java.security.KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry(RECIPIENT_ALIAS, certificate);
        Path location = tempDir.resolve(
                "recipient-" + keyAlgorithm + "-" + notBefore.toEpochMilli() + "-" + keyUsage + ".p12");
        try (OutputStream out = Files.newOutputStream(location)) {
            store.store(out, KEYSTORE_PASSWORD.toCharArray());
        }

        TrustStore trustStore = new TrustStore();
        trustStore.setLocation(location.toUri().toString());
        trustStore.setPassword(KEYSTORE_PASSWORD);
        return trustStore;
    }

    /** Guards the assumption the other cases rest on: the entry really is a trustedCertEntry. */
    @Test
    void theGeneratedStoreHoldsACertificateEntry() throws Exception {
        TrustStore store = validRecipientStore("RSA", 0);
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (var is = Files.newInputStream(Path.of(java.net.URI.create(store.getLocation())))) {
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());
        }
        assertTrue(ks.isCertificateEntry(RECIPIENT_ALIAS));
        assertFalse(ks.isKeyEntry(RECIPIENT_ALIAS));
        assertInstanceOf(Certificate.class, ks.getCertificate(RECIPIENT_ALIAS));
    }
}
