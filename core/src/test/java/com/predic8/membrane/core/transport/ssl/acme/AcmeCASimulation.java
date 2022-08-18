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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;

import static org.bouncycastle.asn1.x509.Extension.*;
import static org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;

public class AcmeCASimulation {
    private static final SecureRandom random = new SecureRandom();
    private KeyPair caKeyPair;
    private X509Certificate caCert;

    X500Name caName = new X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, "Membrane Service Proxy Test CA")
            .build();

    public void init() {
        try {
            caKeyPair = createKey();
            caCert = createCertificate(caName, caKeyPair, caName, caKeyPair.getPublic(), BigInteger.ONE, true, false, null);
        } catch (OperatorCreationException | NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | CertIOException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate createCertificate(
            X500Name issuerName,
            KeyPair issuer,
            X500Name subjectName,
            PublicKey subject, BigInteger serial, boolean isCA, boolean isServer, Extension san) throws OperatorCreationException, CertIOException, CertificateException {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 1);

        Date endDate = calendar.getTime();
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithECDSA").build(issuer.getPrivate());
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuerName, serial, startDate,
                endDate, subjectName, subject);
        BasicConstraints basicConstraints2 = new BasicConstraints(isCA);
        certBuilder.addExtension(basicConstraints, true, basicConstraints2);
        if (isServer) {
            certBuilder.addExtension(keyUsage, false, new KeyUsage(KeyUsage.digitalSignature));
            certBuilder.addExtension(extendedKeyUsage, false, new ExtendedKeyUsage(new KeyPurposeId[]{
                    KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth
            }));
        }
        if (san != null)
            certBuilder.addExtension(subjectAlternativeName, false, san.getParsedValue());

        return new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certBuilder.build(contentSigner));
    }

    private KeyPair createKey() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", PROVIDER_NAME);
        ECGenParameterSpec ecsp = new ECGenParameterSpec("secp384r1");
        kpg.initialize(ecsp, random);
        return kpg.generateKeyPair();
    }

    public String sign(String csrS) {
        try {
            csrS = "-----BEGIN CERTIFICATE REQUEST-----\r\n" +
                Base64.getEncoder().encodeToString(Base64.getUrlDecoder().decode(csrS)) +
                "\r\n-----END CERTIFICATE REQUEST-----\r\n";
            PKCS10CertificationRequest csr = parseCSR(csrS);
            return toStr(sign(csr));
        } catch (IOException | CertificateException | OperatorCreationException e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate sign(PKCS10CertificationRequest csr) throws IOException, CertificateException, OperatorCreationException {
        SubjectPublicKeyInfo pkInfo = csr.getSubjectPublicKeyInfo();
        PublicKey pubkey = new JcaPEMKeyConverter().getPublicKey(pkInfo);

        Extension san = csr.getRequestedExtensions().getExtension(subjectAlternativeName);

        return createCertificate(
                caName,
                caKeyPair,
                csr.getSubject(),
                pubkey,
                new BigInteger("2", 10), false, true, san);
    }

    private PKCS10CertificationRequest parseCSR(String pem) throws IOException {
        PEMParser pemParser = new PEMParser(new StringReader(pem));
        Object parsedObj = pemParser.readObject();

        if (!(parsedObj instanceof PKCS10CertificationRequest))
            throw new RuntimeException("not a CSR.");

        return (PKCS10CertificationRequest) parsedObj;
    }

    public String getCertificate() {
        return toStr(caCert);
    }

    private String toStr(X509Certificate cert) {
        try {
            return "-----BEGIN CERTIFICATE-----\r\n" +
                    Base64.getEncoder().encodeToString(cert.getEncoded()) +
                    "\r\n-----END CERTIFICATE-----\r\n";
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
