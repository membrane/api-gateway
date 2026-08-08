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

import com.predic8.membrane.core.http.XmlDomBody;
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static com.predic8.membrane.core.interceptor.soap.wsse.SignatureReference.By.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class SignatureValidatePartTest extends AbstractWsSecurityTest {

    // ---- signing helpers ----------------------------------------------------------------------

    private void signBody() {
        signer(signature(bodyReference())).handleRequest(exchange);
    }

    private void signBodyWithSecurityTokenReference() {
        SignatureSecurePart signature = signature(bodyReference());
        signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        signer(signature).handleRequest(exchange);
    }

    private void signBodyWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType valueType) {
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(valueType);
        SignatureSecurePart signature = signature(bodyReference());
        signature.setKeyIdentifier(keyIdentifier);
        signer(signature).handleRequest(exchange);
    }

    private void signBodyAndTimestamp() {
        signer(signature(bodyReference(), reference(TIMESTAMP))).handleRequest(exchange);
    }

    // ---- verifying helpers --------------------------------------------------------------------

    private WsSecurityInterceptor verifierTrusting(String truststoreLocation, SignatureReference.By... requiredBy) {
        return verifier(truststoreLocation, requiring(Arrays.stream(requiredBy)
                .map(AbstractWsSecurityTest::reference)
                .toArray(SignatureReference[]::new)));
    }

    /**
     * Signs the current exchange's body and re-parses it, so a test can tamper with the signed
     * document before handing it to the verifier.
     */
    private Document signedAndParsed() throws Exception {
        signBody();
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

    // ---- tests ---------------------------------------------------------------------------------

    @Test
    void referenceWithAnXPathTransformIsRejected() throws Exception {
        // The reference half of a signature-wrapping attack that a URI check alone cannot see: the
        // ds:Reference still names #body-id, so the required-BODY check is satisfied, while the XPath
        // transform means the digest was computed over an entirely different node-set. Only an
        // allowlist of transforms catches this.
        exchangeWithBody(SOAP_BODY);
        Document signed = signedAndParsed();

        Element transforms = firstByTag(signed, DS_NS, "Transforms");
        Element xpathTransform = signed.createElementNS(DS_NS, "ds:Transform");
        xpathTransform.setAttribute("Algorithm", "http://www.w3.org/TR/1999/REC-xpath-19991116");
        Element xpath = signed.createElementNS(DS_NS, "ds:XPath");
        xpath.setTextContent("//*[local-name()='foo']");
        xpathTransform.appendChild(xpath);
        transforms.appendChild(xpathTransform);
        setBody(signed);

        assertFault(verifierTrusting(TRUSTSTORE, BODY), UNSUPPORTED_ALGORITHM);
    }

    @Test
    void referenceWithAnXsltTransformIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document signed = signedAndParsed();

        Element transform = firstByTag(signed, DS_NS, "Transform");
        transform.setAttribute("Algorithm", "http://www.w3.org/TR/1999/REC-xslt-19991116");
        setBody(signed);

        assertFault(verifierTrusting(TRUSTSTORE, BODY), UNSUPPORTED_ALGORITHM);
    }

    @Test
    void sha1SignatureAlgorithmIsRejected() throws Exception {
        // secure/signature can still be configured to produce this for a legacy backend; accepting it
        // on the way in would let any peer downgrade the message to SHA-1.
        exchangeWithBody(SOAP_BODY);
        SignatureSecurePart signature = signature(bodyReference());
        signature.setSignatureAlgorithm(SignatureMethod.RSA_SHA1);
        signer(signature).handleRequest(exchange);

        assertFault(verifierTrusting(TRUSTSTORE, BODY), UNSUPPORTED_ALGORITHM);
    }

    @Test
    void sha1DigestAlgorithmIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureSecurePart signature = signature(bodyReference());
        signature.setDigestAlgorithm(DigestMethod.SHA1);
        signer(signature).handleRequest(exchange);

        assertFault(verifierTrusting(TRUSTSTORE, BODY), UNSUPPORTED_ALGORITHM);
    }

    @Test
    void inclusiveCanonicalizationIsStillAccepted() throws Exception {
        // The transform allowlist rejects transforms that rewrite the node-set, not canonicalization
        // this gateway can itself be configured to produce.
        exchangeWithBody(SOAP_BODY);
        SignatureSecurePart signature = signature(bodyReference());
        signature.setCanonicalizationAlgorithm(CanonicalizationMethod.INCLUSIVE);
        signer(signature).handleRequest(exchange);

        assertEquals(Outcome.CONTINUE, verifierTrusting(TRUSTSTORE, BODY).handleRequest(exchange));
    }

    @Test
    void validSignatureWithX509DataIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signBody();

        assertEquals(Outcome.CONTINUE, verifierTrusting(TRUSTSTORE, BODY).handleRequest(exchange));
    }

    /**
     * All parts share one {@link Document} via the {@link XmlDomBody}. This asserts that a signature
     * added by one part still validates for another afterwards — the case a re-indenting or
     * otherwise byte-altering serialization would break.
     */
    @Test
    void signatureOverASharedDocumentSurvivesTheChain() throws Exception {
        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor secure = securing(new TimestampSecurePart(), signature(bodyReference(), reference(TIMESTAMP)));
        secure.setKeyStore(signingKeyStore(ALIAS_1));
        secure.init(router);
        assertEquals(Outcome.CONTINUE, secure.handleRequest(exchange));
        Document signed = XmlDomBody.read(exchange.getRequest(), doc -> doc);

        assertEquals(Outcome.CONTINUE, verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP).handleRequest(exchange));
        assertSame(signed, XmlDomBody.read(exchange.getRequest(), doc -> doc),
                "The parts must all have worked on the same shared document");
    }

    @Test
    void validSignatureWithSecurityTokenReferenceIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signBodyWithSecurityTokenReference();

        assertEquals(Outcome.CONTINUE, verifierTrusting(TRUSTSTORE, BODY).handleRequest(exchange));
    }

    @Test
    void missingSignatureIsRejected() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header><wsse:Security xmlns:wsse="%s"/></soap:Header>
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS));

        assertFault(verifierTrusting(TRUSTSTORE, BODY), INVALID_SECURITY);
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        ((Element) doc.getElementsByTagName("foo").item(0)).setTextContent("tampered");
        setBody(doc);

        assertFault(verifierTrusting(TRUSTSTORE, BODY), FAILED_CHECK);
    }

    @Test
    void untrustedCertificateIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        // Signed with key1, but alias-truststore2.p12 only trusts key2.
        signBody();

        assertFault(verifierTrusting(TRUSTSTORE_KEY2, BODY), FAILED_CHECK);
    }

    @Test
    void requiredReferenceNotCoveredIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.parse("2024-01-01T00:00:00Z"), "wsu:Id=\"ts-1\""));
        // Only the Body is signed; the pre-existing Timestamp is left uncovered.
        signBody();

        assertFault(verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP), FAILED_CHECK);
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

        assertFault(verifierTrusting(TRUSTSTORE, BODY), FAILED_CHECK);
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

        assertFault(verifierTrusting(TRUSTSTORE, BODY), FAILED_CHECK);
    }

    @Test
    void signatureWithUnsignedDsObjectIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        doc.getElementsByTagNameNS(DS_NS, "Signature").item(0)
                .appendChild(doc.createElementNS(DS_NS, "ds:Object"));
        setBody(doc);

        assertFault(verifierTrusting(TRUSTSTORE, BODY), FAILED_CHECK);
        // Asserted on the reason, not just the fault code: a ds:Object appended to ds:Signature also
        // trips an unmarshalling failure inside the JDK's XMLSignatureFactory, so a bare FailedCheck
        // would not show that the guard against unsigned ds:Signature content is what rejected it.
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

        assertFault(verifierTrusting(TRUSTSTORE, BODY), INVALID_SECURITY);
    }

    @Test
    void nonSoapMessageIsRejected() throws Exception {
        exchangeWithBody("<foo>bar</foo>");

        assertAborts(verifierTrusting(TRUSTSTORE, BODY), 400);
    }

    @Test
    void validSignatureWithKeyIdentifierX509V3IsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signBodyWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType.X509_V3);

        assertEquals(Outcome.CONTINUE, verifierTrusting(TRUSTSTORE, BODY).handleRequest(exchange));
    }

    @Test
    void validSignatureWithKeyIdentifierThumbprintIsAccepted() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signBodyWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1);

        assertEquals(Outcome.CONTINUE, verifierTrusting(TRUSTSTORE, BODY).handleRequest(exchange));
    }

    @Test
    void malformedBase64CertificateIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        Document doc = signedAndParsed();
        ((Element) doc.getElementsByTagNameNS(DS_NS, "X509Certificate").item(0))
                .setTextContent("not base64!!!");
        setBody(doc);

        assertFault(verifierTrusting(TRUSTSTORE, BODY), INVALID_SECURITY_TOKEN);
    }

    @Test
    void unknownThumbprintIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signBodyWithKeyIdentifier(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1);
        // alias-truststore2.p12 only trusts key2, so key1's thumbprint can't be resolved from it.

        assertFault(verifierTrusting(TRUSTSTORE_KEY2, BODY), SECURITY_TOKEN_UNAVAILABLE);
    }

    @Test
    void requiredReferenceWithCustomPrefixIsResolvedViaXmlConfig() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        <c:foo xmlns:c="https://predic8.de/custom">bar</c:foo>
                    </soap:Body>
                </soap:Envelope>
                """);
        WsSecurityInterceptor secure = securing(signature(xpathReference("//c:foo")));
        secure.setKeyStore(signingKeyStore(ALIAS_1));
        secure.setXmlConfig(xmlConfig("c", "https://predic8.de/custom"));
        secure.init(router);
        secure.handleRequest(exchange);

        WsSecurityInterceptor validate = validating(requiring(xpathReference("//c:foo")));
        validate.setTrustStore(trustStore(TRUSTSTORE));
        validate.setXmlConfig(xmlConfig("c", "https://predic8.de/custom"));
        validate.init(router);

        assertEquals(Outcome.CONTINUE, validate.handleRequest(exchange));
    }

    private static final String SOAP_BODY_WITH_TWO_FOOS = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                    <foo>baz</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    @Test
    void rejectsExplicitByCombinedWithXpath() {
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setBy(SignatureReference.By.XPATH);

        assertThrows(RuntimeException.class, () -> verifier(TRUSTSTORE, requiring(reference)));
    }

    @Test
    void rejectsNonXpathByCombinedWithXpath() {
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setBy(BODY);

        assertThrows(RuntimeException.class, () -> verifier(TRUSTSTORE, requiring(reference)));
    }

    @Test
    void rejectsXpathByWithoutXpath() {
        assertThrows(RuntimeException.class,
                () -> verifier(TRUSTSTORE, requiring(reference(SignatureReference.By.XPATH))));
    }

    @Test
    void rejectsMissingTrustStore() {
        WsSecurityInterceptor validate = validating(requiring(bodyReference()));

        assertThrows(RuntimeException.class, () -> validate.init(router));
    }

    @Test
    void rejectsSignatureWithoutRequiredReferences() {
        assertThrows(RuntimeException.class, () -> verifier(TRUSTSTORE, new SignatureValidatePart()));
    }

    @Test
    void requiredReferenceWithMultipleXPathMatchesIsAcceptedWhenAllAreSigned() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TWO_FOOS);
        signer(signature(xpathReference("//*[local-name()='foo']"))).handleRequest(exchange);

        assertEquals(Outcome.CONTINUE,
                verifier(TRUSTSTORE, requiring(xpathReference("//*[local-name()='foo']"))).handleRequest(exchange));
    }

    @Test
    void requiredReferenceWithMultipleXPathMatchesIsRejectedWhenOnlyOneIsSigned() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TWO_FOOS);
        // Only the first foo is signed explicitly; the second is left uncovered.
        signer(signature(xpathReference("(//*[local-name()='foo'])[1]"))).handleRequest(exchange);

        assertFault(verifier(TRUSTSTORE, requiring(xpathReference("//*[local-name()='foo']"))), FAILED_CHECK);
    }

    @Test
    void freshTimestampIsAccepted() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.now()));
        signBodyAndTimestamp();

        assertEquals(Outcome.CONTINUE, verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP).handleRequest(exchange));
    }

    @Test
    void expiredTimestampIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.now().minus(Duration.ofHours(1))));
        signBodyAndTimestamp();

        assertFault(verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP), MESSAGE_EXPIRED);
    }

    @Test
    void futureTimestampIsRejected() throws Exception {
        exchangeWithBody(soapBodyWithTimestamp(Instant.now().plus(Duration.ofHours(1))));
        signBodyAndTimestamp();

        assertFault(verifierTrusting(TRUSTSTORE, BODY, TIMESTAMP), FAILED_CHECK);
    }

    @Test
    void signedUsernameTokenIsAcceptedWhenRequired() throws Exception {
        // The signed-UsernameToken policy: the signature is what binds the credential to this message,
        // so a captured token cannot be replayed against a different body.
        exchangeWithBody(SOAP_BODY);
        UsernameTokenSecurePart usernameToken = new UsernameTokenSecurePart();
        usernameToken.setUsername("alice");
        usernameToken.setPassword("secret");
        WsSecurityInterceptor signer = securing(usernameToken,
                signature(bodyReference(), reference(USERNAME_TOKEN)));
        signer.setKeyStore(signingKeyStore(ALIAS_1));
        signer.init(router);
        assertEquals(Outcome.CONTINUE, signer.handleRequest(exchange));

        assertEquals(Outcome.CONTINUE,
                verifierTrusting(TRUSTSTORE, BODY, USERNAME_TOKEN).handleRequest(exchange));
    }

    @Test
    void unsignedUsernameTokenIsRejectedWhenRequired() throws Exception {
        // Only the Body is signed, so the token could have been swapped in transit.
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s">
                            <wsse:UsernameToken>
                                <wsse:Username>alice</wsse:Username>
                                <wsse:Password>secret</wsse:Password>
                            </wsse:UsernameToken>
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS));
        signBody();

        assertFault(verifierTrusting(TRUSTSTORE, BODY, USERNAME_TOKEN), FAILED_CHECK);
    }

    @Test
    void requiredUsernameTokenReferenceWithNoTokenAtAllIsRejected() throws Exception {
        exchangeWithBody(SOAP_BODY);
        signBody();

        assertFault(verifierTrusting(TRUSTSTORE, BODY, USERNAME_TOKEN), FAILED_CHECK);
    }

    @Test
    void rejectsNegativeClockSkew() {
        assertThrows(RuntimeException.class, () -> new SignatureValidatePart().setClockSkew("PT-5M"));
    }
}
