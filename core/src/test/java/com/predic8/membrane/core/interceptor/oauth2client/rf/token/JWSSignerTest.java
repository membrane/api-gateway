/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static java.math.BigInteger.ONE;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWSSignerTest {

    public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERT = "-----END CERTIFICATE-----";

    JWSSigner JWSSigner;
    JwtClaims jwtClaims;
    KeyPairGenerator keyPairGenerator;
    KeyPair keyPair;
    String pemBlock;

    @BeforeEach
    void setup() throws IOException, NoSuchAlgorithmException, OperatorCreationException {
        keyPair = generateKeyPair();
        pemBlock = String.format("%s\n%s\n%s", BEGIN_CERT, printBase64Binary(generateX509Certificate().getEncoded()), END_CERT);
        JWSSigner = new JWSSigner(keyPair, pemBlock);
        generateJwtClaims();
    }

    @Test
    void generateSignedJWSTestNotNull() throws JoseException {
        assertNotNull(JWSSigner.generateSignedJWS(jwtClaims.toJson()));
    }

    @Test
    void headerContainsFingerPrint() throws JoseException, IOException {
        assertTrue(decodeJWTChunks(getJWTChunks(JWSSigner.generateSignedJWS(jwtClaims.toJson()))).get("header").containsKey("x5t"));
    }

    @Test
    void headerContainsSub() throws JoseException, IOException {
        assertTrue(decodeJWTChunks(getJWTChunks(JWSSigner.generateSignedJWS(jwtClaims.toJson()))).get("payload").containsKey("sub"));
    }

    private void generateJwtClaims() {
        try {
            NumericDate expiration = NumericDate.now();
            expiration.addSeconds(300);

            // see https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-certificate-credentials
            jwtClaims = new JwtClaims();
            jwtClaims.setSubject("Test Subject");
            jwtClaims.setAudience("Test Audience");
            jwtClaims.setIssuer(jwtClaims.getSubject());
            jwtClaims.setJwtId("testId");
            jwtClaims.setIssuedAtToNow();
            jwtClaims.setExpirationTime(expiration);
            jwtClaims.setNotBeforeMinutesInThePast(2f);

        } catch (MalformedClaimException e) {
            throw new RuntimeException(e);
        }
    }

    private X509CertificateHolder generateX509Certificate() throws OperatorCreationException {
        X509v3CertificateBuilder x509v3CertificateBuilder = new X509v3CertificateBuilder(
                new X500Name("CN=Membrane,O=p8,L=Bonn,C=DE"),
                ONE,
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(60, SECONDS)),
                new X500Name("CN=Membrane,O=p8,L=Bonn,C=DE"),
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
        return x509v3CertificateBuilder.build(new JcaContentSignerBuilder("SHA256WithRSA").setProvider(new BouncyCastleProvider()).build(keyPair.getPrivate()));
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private String[] getJWTChunks(String jwt) {
        return jwt.split("\\.");
    }

    private Map<String, Map<String, String>> decodeJWTChunks(String[] chunks) throws IOException {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        return Map.of("header", new ObjectMapper().readValue(decoder.decode(chunks[0]), new TypeReference<>() {
                }),
                "payload", new ObjectMapper().readValue(decoder.decode(chunks[1]), new TypeReference<>() {
                }));
    }
}