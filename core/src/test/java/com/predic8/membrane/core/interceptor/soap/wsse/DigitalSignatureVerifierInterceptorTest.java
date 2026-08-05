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
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.SignatureReference.By.BODY;
import static com.predic8.membrane.core.interceptor.soap.wsse.SignatureReference.By.TIMESTAMP;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class DigitalSignatureVerifierInterceptorTest extends AbstractWsseInterceptorTest {

    DigitalSignatureVerifierInterceptor verifier;

    @BeforeEach
    void setUp() {
        verifier = new DigitalSignatureVerifierInterceptor();
    }

    private DigitalSignatureInterceptor signer(SignatureReference... references) {
        DigitalSignatureInterceptor signer = new DigitalSignatureInterceptor();
        signer.setKeyStore(signingKeyStore(ALIAS_1));
        signer.setReferences(List.of(references));
        return signer;
    }

    private DigitalSignatureInterceptor bodySigner() {
        DigitalSignatureInterceptor signer = signer(bodyReference());
        signer.init(router);
        return signer;
    }

    private DigitalSignatureInterceptor bodySignerWithSecurityTokenReference() {
        DigitalSignatureInterceptor signer = signer(bodyReference());
        signer.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        signer.init(router);
        return signer;
    }

    private DigitalSignatureInterceptor bodySignerWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType valueType) {
        DigitalSignatureInterceptor signer = signer(bodyReference());
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(valueType);
        signer.setKeyIdentifier(keyIdentifier);
        signer.init(router);
        return signer;
    }

    private DigitalSignatureInterceptor bodyAndTimestampSigner() {
        DigitalSignatureInterceptor signer = signer(bodyReference(), reference(TIMESTAMP));
        signer.init(router);
        return signer;
    }

    private void verifierTrusting(String truststoreLocation, SignatureReference.By... requiredBy) {
        TrustStore trustStore = new TrustStore();
        trustStore.setLocation(truststoreLocation);
        trustStore.setPassword(KEYSTORE_PASSWORD);
        verifier.setTrustStore(trustStore);
        verifier.setRequiredReferences(Arrays.stream(requiredBy)
                .map(AbstractWsseInterceptorTest::reference)
                .toList());
        verifier.init(router);
    }

    /**
     * Signs the current exchange's body and re-parses it, so a test can tamper with the signed
     * document before handing it to the verifier.
     */
    private Document signedAndParsed() throws Exception {
        bodySigner().handleRequest(exchange);
        return parseBody();
    }

    private static Element decoyBody(Document doc, String wsuId) {
        Element decoyBody = doc.createElementNS(SOAP_NS, "soap:Body");
        decoyBody.setAttributeNS(WSU_NS, "wsu:Id", wsuId);
        Element decoyFoo = doc.createElementNS(null, "foo");
        decoyFoo.setTextContent("PWNED");
        decoyBody.appendChild(decoyFoo);
        return decoyBody;
    }

    private static String soapBodyWithTimestamp(Instant created) {
        return soapBodyWithTimestamp(created, "");
    }

    private static String soapBodyWithTimestamp(Instant created, String timestampId) {
        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s">
                            <wsu:Timestamp xmlns:wsu="%s" %s>
                                <wsu:Created>%s</wsu:Created>
                            </wsu:Timestamp>
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body>
                        <foo>bar</foo>
                    </soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS, WSU_NS, timestampId, created);
    }

    @Test
    void validSignatureWithX509DataIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        bodySigner().handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void validSignatureWithSecurityTokenReferenceIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        bodySignerWithSecurityTokenReference().handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void missingSignatureIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 401);
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        ((Element) doc.getElementsByTagName("foo").item(0)).setTextContent("tampered");
        setBody(doc);

        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 403);
    }

    @Test
    void untrustedCertificateIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        // Signed with key1, but alias-truststore2.p12 only trusts key2.
        bodySigner().handleRequest(exchange);
        verifierTrusting(TRUSTSTORE_KEY2, BODY);

        assertAborts(verifier, 403);
    }

    @Test
    void requiredReferenceNotCoveredIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.parse("2024-01-01T00:00:00Z"), "wsu:Id=\"ts-1\""));
        // Only the Body is signed; the pre-existing Timestamp is left uncovered.
        bodySigner().handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP);

        assertAborts(verifier, 403);
    }

    @Test
    void signatureWrappingAttackIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        Element envelope = doc.getDocumentElement();
        Element header = (Element) envelope.getElementsByTagNameNS(SOAP_NS, "Header").item(0);
        Element signedBody = (Element) envelope.getElementsByTagNameNS(SOAP_NS, "Body").item(0);
        String signedBodyId = signedBody.getAttributeNS(WSU_NS, "Id");
        assertFalse(signedBodyId.isEmpty());

        // Move the genuinely signed Body into the (unprocessed-by-downstream) Header, and plant a
        // fresh, unsigned decoy Body - sharing the SAME Id, to try to "borrow" its coverage - as
        // the real structural child of Envelope. This is the classic SOAP Body wrapping attack.
        header.appendChild(envelope.removeChild(signedBody));
        envelope.appendChild(decoyBody(doc, signedBodyId));
        setBody(doc);

        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 403);
    }

    @Test
    void wrappingWithFreshIdAndUnsignedManifestReferenceIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        Element envelope = doc.getDocumentElement();
        Element signature = (Element) doc.getElementsByTagNameNS(DS_NS, "Signature").item(0);
        Element signedBody = (Element) envelope.getElementsByTagNameNS(SOAP_NS, "Body").item(0);

        // Variant of the wrapping attack that the same-Id check alone does NOT catch: park the
        // genuinely signed Body (keeping its Id) inside an unsigned ds:Object, give the decoy a
        // FRESH Id, and declare that fresh Id "covered" with a ds:Reference planted in an unsigned
        // ds:Manifest. SignedInfo/SignatureValue stay untouched, so the signature itself still
        // validates - coverage must be decided from SignedInfo only.
        Element object = doc.createElementNS(DS_NS, "ds:Object");
        object.appendChild(envelope.removeChild(signedBody));
        Element manifest = doc.createElementNS(DS_NS, "ds:Manifest");
        Element planted = doc.createElementNS(DS_NS, "ds:Reference");
        planted.setAttribute("URI", "#decoy-1");
        manifest.appendChild(planted);
        object.appendChild(manifest);
        signature.appendChild(object);

        envelope.appendChild(decoyBody(doc, "decoy-1"));
        setBody(doc);

        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 403);
    }

    @Test
    void signatureWithUnsignedDsObjectIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        doc.getElementsByTagNameNS(DS_NS, "Signature").item(0)
                .appendChild(doc.createElementNS(DS_NS, "ds:Object"));
        setBody(doc);

        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 403);
        // Asserted on the reason, not just the status: a ds:Object appended to ds:Signature also
        // trips an unmarshalling failure inside the JDK's XMLSignatureFactory, so a bare 403 would
        // not show that the guard against unsigned ds:Signature content is what rejected it.
        assertTrue(exchange.getResponse().getBodyAsStringDecoded().contains("unsigned ds:Object"),
                "Expected the rejection to name the unsigned ds:Object");
    }

    @Test
    void multipleSignaturesAreRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        Element security = (Element) doc.getElementsByTagNameNS(WSSE_NS, "Security").item(0);
        security.appendChild(doc.getElementsByTagNameNS(DS_NS, "Signature").item(0).cloneNode(true));
        setBody(doc);

        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 403);
    }

    @Test
    void nonSoapMessageIsRejected() throws Exception {
        exchangeWithBody("<foo>bar</foo>");
        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 400);
    }

    @Test
    void validSignatureWithKeyIdentifierX509V3IsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        bodySignerWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType.X509_V3).handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void validSignatureWithKeyIdentifierThumbprintIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        bodySignerWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1).handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void malformedBase64CertificateIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        ((Element) doc.getElementsByTagNameNS(DS_NS, "X509Certificate").item(0))
                .setTextContent("not base64!!!");
        setBody(doc);

        verifierTrusting(TRUSTSTORE, BODY);

        assertAborts(verifier, 403);
    }

    @Test
    void unknownThumbprintIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        bodySignerWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1).handleRequest(exchange);
        // alias-truststore2.p12 only trusts key2, so key1's thumbprint can't be resolved from it.
        verifierTrusting(TRUSTSTORE_KEY2, BODY);

        assertAborts(verifier, 403);
    }

    @Test
    void freshTimestampIsAccepted() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.now()));
        bodyAndTimestampSigner().handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void expiredTimestampIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.now().minus(Duration.ofHours(1))));
        bodyAndTimestampSigner().handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP);

        assertAborts(verifier, 403);
    }

    @Test
    void futureTimestampIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.now().plus(Duration.ofHours(1))));
        bodyAndTimestampSigner().handleRequest(exchange);
        verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP);

        assertAborts(verifier, 403);
    }
}
