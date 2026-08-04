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

import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class DigitalSignatureVerifierInterceptorTest {

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String KEYSTORE_PASSWORD = "secret";
    private static final String ALIAS_1 = "key1";
    private static final String ALIAS_2 = "key2";

    private static final String SOAP_BODY = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    private static final String SOAP_BODY_WITH_TIMESTAMP = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Header>
                    <wsse:Security xmlns:wsse="%s">
                        <wsu:Timestamp xmlns:wsu="%s" wsu:Id="ts-1">
                            <wsu:Created>2024-01-01T00:00:00Z</wsu:Created>
                        </wsu:Timestamp>
                    </wsse:Security>
                </soap:Header>
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """.formatted(WSSE_NS, WSU_NS);

    DefaultRouter router;
    Exchange exchange;
    DigitalSignatureVerifierInterceptor verifier;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
        verifier = new DigitalSignatureVerifierInterceptor();
    }

    private DigitalSignatureInterceptor signerFor(String keystoreLocation, String alias, boolean useSecurityTokenReference) {
        DigitalSignatureInterceptor signer = new DigitalSignatureInterceptor();
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation(keystoreLocation);
        keyStore.setKeyPassword(KEYSTORE_PASSWORD);
        keyStore.setKeyAlias(alias);
        signer.setKeyStore(keyStore);
        if (useSecurityTokenReference) {
            signer.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        }
        SignatureReference bodyRef = new SignatureReference();
        bodyRef.setBy(SignatureReference.By.BODY);
        signer.setReferences(List.of(bodyRef));
        signer.init(router);
        return signer;
    }

    private DigitalSignatureInterceptor signerWithKeyIdentifier(String keystoreLocation, String alias,
                                                                 KeyIdentifierKeyInfo.ValueType valueType) {
        DigitalSignatureInterceptor signer = new DigitalSignatureInterceptor();
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation(keystoreLocation);
        keyStore.setKeyPassword(KEYSTORE_PASSWORD);
        keyStore.setKeyAlias(alias);
        signer.setKeyStore(keyStore);
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(valueType);
        signer.setKeyIdentifier(keyIdentifier);
        SignatureReference bodyRef = new SignatureReference();
        bodyRef.setBy(SignatureReference.By.BODY);
        signer.setReferences(List.of(bodyRef));
        signer.init(router);
        return signer;
    }

    private void verifierWithTruststore(String truststoreLocation, SignatureReference.By... requiredBy) {
        TrustStore trustStore = new TrustStore();
        trustStore.setLocation(truststoreLocation);
        trustStore.setPassword(KEYSTORE_PASSWORD);
        verifier.setTrustStore(trustStore);
        verifier.setRequiredReferences(List.of(requiredBy).stream().map(by -> {
            SignatureReference ref = new SignatureReference();
            ref.setBy(by);
            return ref;
        }).toList());
        verifier.init(router);
    }

    private void exchangeWithBody(String body) throws Exception {
        exchange = new Exchange(null);
        exchange.setRequest(new Request.Builder()
                .post("/service")
                .contentType(TEXT_XML)
                .body(body)
                .build());
    }

    private Document parseCurrentBody() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(exchange.getRequest().getBodyAsStream());
    }

    private void setBody(Document doc) throws Exception {
        exchange.getRequest().setBodyContent(
                com.predic8.membrane.core.util.xml.XMLUtil.xmlNode2String(doc).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void validSignatureWithX509DataIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerFor("classpath:/alias-keystore.p12", ALIAS_1, false).handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void validSignatureWithSecurityTokenReferenceIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerFor("classpath:/alias-keystore.p12", ALIAS_1, true).handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void missingSignatureIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(401, exchange.getResponse().getStatusCode());
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerFor("classpath:/alias-keystore.p12", ALIAS_1, false).handleRequest(exchange);

        Document doc = parseCurrentBody();
        Element foo = (Element) doc.getElementsByTagName("foo").item(0);
        foo.setTextContent("tampered");
        setBody(doc);

        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void untrustedCertificateIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        // Signed with key1, but alias-truststore2.p12 only trusts key2.
        signerFor("classpath:/alias-keystore.p12", ALIAS_1, false).handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore2.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void requiredReferenceNotCoveredIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TIMESTAMP);
        // Only the Body is signed; the pre-existing Timestamp is left uncovered.
        signerFor("classpath:/alias-keystore.p12", ALIAS_1, false).handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY, SignatureReference.By.TIMESTAMP);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void signatureWrappingAttackIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerFor("classpath:/alias-keystore.p12", ALIAS_1, false).handleRequest(exchange);

        Document doc = parseCurrentBody();
        Element envelope = doc.getDocumentElement();
        Element header = (Element) envelope.getElementsByTagNameNS(SOAP_NS, "Header").item(0);
        Element signedBody = (Element) envelope.getElementsByTagNameNS(SOAP_NS, "Body").item(0);
        String signedBodyId = signedBody.getAttributeNS(WSU_NS, "Id");
        assertFalse(signedBodyId.isEmpty());

        // Move the genuinely signed Body into the (unprocessed-by-downstream) Header, and plant a
        // fresh, unsigned decoy Body - sharing the SAME Id, to try to "borrow" its coverage - as
        // the real structural child of Envelope. This is the classic SOAP Body wrapping attack.
        Node movedBody = envelope.removeChild(signedBody);
        header.appendChild(movedBody);

        Element decoyBody = doc.createElementNS(SOAP_NS, "soap:Body");
        decoyBody.setAttributeNS(WSU_NS, "wsu:Id", signedBodyId);
        Element decoyFoo = doc.createElementNS(null, "foo");
        decoyFoo.setTextContent("PWNED");
        decoyBody.appendChild(decoyFoo);
        envelope.appendChild(decoyBody);

        setBody(doc);

        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void multipleSignaturesAreRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerFor("classpath:/alias-keystore.p12", ALIAS_1, false).handleRequest(exchange);

        Document doc = parseCurrentBody();
        Element security = (Element) doc.getElementsByTagNameNS(WSSE_NS, "Security").item(0);
        Element signature = (Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0);
        security.appendChild(signature.cloneNode(true));
        setBody(doc);

        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void nonSoapMessageIsRejected() throws Exception {
        exchangeWithBody("<foo>bar</foo>");
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void validSignatureWithKeyIdentifierX509V3IsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerWithKeyIdentifier("classpath:/alias-keystore.p12", ALIAS_1, KeyIdentifierKeyInfo.ValueType.X509_V3)
                .handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void validSignatureWithKeyIdentifierThumbprintIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerWithKeyIdentifier("classpath:/alias-keystore.p12", ALIAS_1, KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1)
                .handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void unknownThumbprintIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signerWithKeyIdentifier("classpath:/alias-keystore.p12", ALIAS_1, KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1)
                .handleRequest(exchange);
        // alias-truststore2.p12 only trusts key2, so key1's thumbprint can't be resolved from it.
        verifierWithTruststore("classpath:/alias-truststore2.p12", SignatureReference.By.BODY);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void freshTimestampIsAccepted() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(java.time.Instant.now()));
        signerWithTimestampReference("classpath:/alias-keystore.p12", ALIAS_1).handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY, SignatureReference.By.TIMESTAMP);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void expiredTimestampIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(java.time.Instant.now().minus(java.time.Duration.ofHours(1))));
        signerWithTimestampReference("classpath:/alias-keystore.p12", ALIAS_1).handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY, SignatureReference.By.TIMESTAMP);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void futureTimestampIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(java.time.Instant.now().plus(java.time.Duration.ofHours(1))));
        signerWithTimestampReference("classpath:/alias-keystore.p12", ALIAS_1).handleRequest(exchange);
        verifierWithTruststore("classpath:/alias-truststore.p12", SignatureReference.By.BODY, SignatureReference.By.TIMESTAMP);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    private static String soapBodyWithTimestamp(java.time.Instant created) {
        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s">
                            <wsu:Timestamp xmlns:wsu="%s">
                                <wsu:Created>%s</wsu:Created>
                            </wsu:Timestamp>
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body>
                        <foo>bar</foo>
                    </soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS, WSU_NS, created);
    }

    private DigitalSignatureInterceptor signerWithTimestampReference(String keystoreLocation, String alias) {
        DigitalSignatureInterceptor signer = new DigitalSignatureInterceptor();
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation(keystoreLocation);
        keyStore.setKeyPassword(KEYSTORE_PASSWORD);
        keyStore.setKeyAlias(alias);
        signer.setKeyStore(keyStore);
        SignatureReference bodyRef = new SignatureReference();
        bodyRef.setBy(SignatureReference.By.BODY);
        SignatureReference timestampRef = new SignatureReference();
        timestampRef.setBy(SignatureReference.By.TIMESTAMP);
        signer.setReferences(List.of(bodyRef, timestampRef));
        signer.init(router);
        return signer;
    }
}
