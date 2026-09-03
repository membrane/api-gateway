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

import com.predic8.membrane.core.interceptor.Outcome;
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
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static com.predic8.membrane.core.interceptor.soap.wsse.SignatureReference.By.BODY;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.FAILED_CHECK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The checks on the signing certificate that a PKIX path validation does not make on this gateway's
 * behalf, because the certificate the message supplies is itself the trust anchor and so is not part
 * of any path: that it is currently valid, and that its declared key usage permits signing.
 * <p>
 * Separate from {@link SignatureValidatePartTest} because these need purpose-built certificates -
 * expired, or usable only for key encipherment - rather than the shared keystore fixtures.
 */
class SignatureValidateCertificateTest extends AbstractWsSecurityTest {

    private static final String KEY_PASSWORD = "secret";

    @TempDir
    Path tempDir;

    @Test
    void expiredSigningCertificateIsRejected() throws Exception {
        Instant now = Instant.now();
        assertFaultFor(certificate(now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(1)), 0));
    }

    @Test
    void notYetValidSigningCertificateIsRejected() throws Exception {
        Instant now = Instant.now();
        assertFaultFor(certificate(now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)), 0));
    }

    @Test
    void signingCertificateWithoutASigningKeyUsageIsRejected() throws Exception {
        Instant now = Instant.now();
        assertFaultFor(certificate(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)),
                KeyUsage.keyEncipherment));
    }

    @Test
    void validSigningCertificateIsAccepted() throws Exception {
        Instant now = Instant.now();
        Signing signing = certificate(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)),
                KeyUsage.digitalSignature);

        exchangeWithBody(SOAP_BODY);
        signing.sign();

        assertEquals(Outcome.CONTINUE, signing.verifier().handleRequest(exchange));
    }

    @Test
    void certificateWithNoKeyUsageExtensionIsAccepted() throws Exception {
        // An absent keyUsage extension leaves the key unconstrained, so it must not be read as "may not
        // sign" - that would reject the many certificates that simply do not declare one.
        Instant now = Instant.now();
        Signing signing = certificate(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)), 0);

        exchangeWithBody(SOAP_BODY);
        signing.sign();

        assertEquals(Outcome.CONTINUE, signing.verifier().handleRequest(exchange));
    }

    private void assertFaultFor(Signing signing) throws Exception {
        exchangeWithBody(SOAP_BODY);
        signing.sign();

        assertFault(signing.verifier(), FAILED_CHECK);
    }

    /**
     * A self-signed certificate that is both the signing certificate and the only trust anchor, so
     * that the certificate under test is the one {@code checkTrusted} has to judge on its own.
     *
     * @param keyUsage the {@link KeyUsage} bits to declare, or 0 to omit the extension entirely
     */
    private Signing certificate(Instant notBefore, Instant notAfter, int keyUsage) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        X500Name subject = new X500Name("CN=signer.example.com");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(1), Date.from(notBefore), Date.from(notAfter), subject,
                keyPair.getPublic());
        if (keyUsage != 0) {
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
        }
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate())));

        // The same store serves as keystore and truststore: it holds the private key for signing and
        // the certificate as the trust anchor for verifying.
        java.security.KeyStore store = java.security.KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("signer", keyPair.getPrivate(), KEY_PASSWORD.toCharArray(),
                new java.security.cert.Certificate[]{certificate});
        Path location = tempDir.resolve("signer-" + notBefore.toEpochMilli() + "-" + keyUsage + ".p12");
        try (OutputStream out = Files.newOutputStream(location)) {
            store.store(out, KEY_PASSWORD.toCharArray());
        }
        return new Signing(location);
    }

    /** The keystore/truststore pair built for one test, and the elements that use it. */
    private class Signing {

        private final String location;

        Signing(Path location) {
            this.location = location.toUri().toString();
        }

        void sign() {
            com.predic8.membrane.core.config.security.KeyStore keyStore =
                    new com.predic8.membrane.core.config.security.KeyStore();
            keyStore.setLocation(location);
            keyStore.setPassword(KEY_PASSWORD);
            keyStore.setKeyPassword(KEY_PASSWORD);

            WsSecurityInterceptor wsSecurity = securing(signature(bodyReference()));
            wsSecurity.setKeyStore(keyStore);
            wsSecurity.init(router);
            wsSecurity.handleRequest(exchange);
        }

        WsSecurityInterceptor verifier() {
            com.predic8.membrane.core.config.security.TrustStore trustStore =
                    new com.predic8.membrane.core.config.security.TrustStore();
            trustStore.setLocation(location);
            trustStore.setPassword(KEY_PASSWORD);

            WsSecurityInterceptor wsSecurity = validating(requiring(reference(BODY)));
            wsSecurity.setTrustStore(trustStore);
            wsSecurity.init(router);
            return wsSecurity;
        }
    }
}
