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
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.INVALID_SECURITY;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class SignatureSecurePartTest extends AbstractWsSecurityTest {

    private static final String SOAP_BODY_WITH_ID = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                            xmlns:wsu="%s">
                <soap:Body wsu:Id="existing-1">
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """.formatted(WSU_NS);

    private static final String SOAP_BODY_WITH_EXISTING_HEADER = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                            xmlns:test="https://example.com/test">
                <soap:Header>
                    <test:Existing/>
                </soap:Header>
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    private static final String SOAP_BODY_WITH_TWO_FOOS = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                    <foo>baz</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    /** Synthetic, not a real certificate - only its identity as "not ours" matters here. */
    private static final String PEER_CERTIFICATE = "cGVlci1zdXBwbGllZC1jZXJ0aWZpY2F0ZQ==";

    private static final String SOAP_BODY_WITH_PEER_BST = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Header>
                    <wsse:Security xmlns:wsse="%s" xmlns:wsu="%s">
                        <wsse:BinarySecurityToken wsu:Id="peer-token-1"
                            ValueType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3"
                            >%s</wsse:BinarySecurityToken>
                    </wsse:Security>
                </soap:Header>
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """.formatted(WSSE_NS, WSU_NS, PEER_CERTIFICATE);

    /** Signs {@code body} with the given references and returns the resulting document. */
    private Document signAndParse(String body, SignatureReference... references) throws Exception {
        exchangeWithBody(body);
        WsSecurityInterceptor wsSecurity = signer(signature(references));
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
        return parseBody();
    }

    private Document signBodyAndParse() throws Exception {
        return signAndParse(SOAP_BODY, bodyReference());
    }

    @Test
    void signsBodyByDefault() throws Exception {
        Document result = signBodyAndParse();

        firstByTag(result, WSSE_NS, "Security");
        firstByTag(result, DS_NS, "Signature");

        String bodyId = firstByTag(result, SOAP_NS, "Body").getAttributeNS(WSU_NS, "Id");
        assertFalse(bodyId.isEmpty());
        assertEquals("#" + bodyId, firstByTag(result, DS_NS, "Reference").getAttribute("URI"));

        assertFalse(firstByTag(result, DS_NS, "X509Certificate").getTextContent().isBlank());
        assertFalse(firstByTag(result, DS_NS, "SignatureValue").getTextContent().isBlank());
    }

    @Test
    void signatureValueHasNoEmbeddedWhitespace() throws Exception {
        // The JDK's XML-DSig provider wraps SignatureValue's Base64 text at 76 characters by
        // default; Membrane strips that for compatibility with consumers expecting one line.
        String signatureValue = firstByTag(signBodyAndParse(), DS_NS, "SignatureValue").getTextContent();

        assertFalse(signatureValue.matches("(?s).*\\s.*"));
    }

    @Test
    void signatureIsCryptographicallyVerifiable() throws Exception {
        assertSignatureIsValid(signBodyAndParse());
    }

    /**
     * The declaration must name the encoding; whether it also carries a standalone
     * pseudo-attribute does not matter, as none of it is part of any digest.
     * Encoding is needed for the signing.
     */
    @Test
    void signedBodyStartsWithXmlDeclaration() throws Exception {
        signBodyAndParse();

        assertTrue(rawBody().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\""), rawBody());
    }

    @Test
    void multipleReferencesAreAllSigned() throws Exception {
        Document result = signAndParse(SOAP_BODY, bodyReference(), xpathReference("//*[local-name()='foo']"));

        assertEquals(2, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    @Test
    void signatureElementHasIdAndInclusiveNamespaces() throws Exception {
        Document result = signBodyAndParse();

        assertTrue(firstByTag(result, DS_NS, "Signature").getAttribute("Id").startsWith("SIG-"));
        assertEquals("soap", inclusiveNamespacesPrefixList(firstByTag(result, DS_NS, "CanonicalizationMethod")));
        assertNoInclusiveNamespaces(firstByTag(result, DS_NS, "Transform"));

        assertSignatureIsValid(result);
    }

    @Test
    void securityHeaderGetsMustUnderstand() throws Exception {
        Element security = firstByTag(signBodyAndParse(), WSSE_NS, "Security");

        assertEquals("1", security.getAttributeNS(SOAP_NS, "mustUnderstand"));
    }

    /**
     * The fresh header goes last so header blocks the message already carried - which may be
     * targeted at other actors, and whose order is theirs to decide - keep their relative position.
     */
    @Test
    void securityIsAppendedAfterExistingHeaderBlocks() throws Exception {
        Document result = signAndParse(SOAP_BODY_WITH_EXISTING_HEADER, bodyReference());

        List<Element> headerBlocks = childElements(firstByTag(result, SOAP_NS, "Header"));
        assertEquals(2, headerBlocks.size());
        assertEquals("Existing", headerBlocks.getFirst().getLocalName());
        assertEquals(WSSE_NS, headerBlocks.getLast().getNamespaceURI());
        assertEquals("Security", headerBlocks.getLast().getLocalName());
    }

    private static List<Element> childElements(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }

    @Test
    void existingWsuIdIsReusedNotOverwritten() throws Exception {
        Document result = signAndParse(SOAP_BODY_WITH_ID, bodyReference());

        assertEquals("#existing-1", firstByTag(result, DS_NS, "Reference").getAttribute("URI"));
    }

    @Test
    void xpathMatchingZeroElementsFaults() throws Exception {
        exchangeWithBody(SOAP_BODY);

        assertFault(signer(signature(xpathReference("//*[local-name()='doesNotExist']"))), INVALID_SECURITY);
    }

    @Test
    void xpathMatchingMultipleElementsSignsEachOne() throws Exception {
        Document result = signAndParse(SOAP_BODY_WITH_TWO_FOOS, xpathReference("//*[local-name()='foo']"));

        NodeList foos = result.getElementsByTagNameNS("*", "foo");
        assertEquals(2, foos.getLength());
        assertFalse(((Element) foos.item(0)).getAttributeNS(WSU_NS, "Id").isEmpty());
        assertFalse(((Element) foos.item(1)).getAttributeNS(WSU_NS, "Id").isEmpty());
        assertNotEquals(((Element) foos.item(0)).getAttributeNS(WSU_NS, "Id"),
                ((Element) foos.item(1)).getAttributeNS(WSU_NS, "Id"));

        assertEquals(2, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    @Test
    void explicitIdWithMultipleXPathMatchesFaults() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TWO_FOOS);
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setId("explicit-id");

        assertFault(signer(signature(reference)), INVALID_SECURITY);
    }

    @Test
    void xpathWithBuiltInSoapPrefixResolvesWithoutXmlConfig() throws Exception {
        Document result = signAndParse(SOAP_BODY, xpathReference("//soap:Body"));

        assertEquals(1, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    private static final String SOAP_BODY_WITH_CUSTOM_NS = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <c:foo xmlns:c="https://predic8.de/custom">bar</c:foo>
                </soap:Body>
            </soap:Envelope>
            """;

    private Document signWithCustomNamespaceAndParse(String xpath) throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_CUSTOM_NS);
        WsSecurityInterceptor wsSecurity = securing(signature(xpathReference(xpath)));
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));
        wsSecurity.setXmlConfig(xmlConfig("c", "https://predic8.de/custom"));
        wsSecurity.init(router);

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
        return parseBody();
    }

    @Test
    void xpathWithCustomPrefixResolvesViaXmlConfig() throws Exception {
        Document result = signWithCustomNamespaceAndParse("//c:foo");

        assertEquals(1, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    @Test
    void xmlConfigMergesCustomPrefixesWithBuiltIns() throws Exception {
        Document result = signWithCustomNamespaceAndParse("//soap:Body/c:foo");

        assertEquals(1, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    @Test
    void passwordIsUsedAsKeyPasswordFallback() throws Exception {
        // Only `password` set, no distinct `keyPassword` - common for a PKCS12 keystore where both
        // are the same.
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation(KEYSTORE);
        keyStore.setKeyAlias(ALIAS_1);
        keyStore.setPassword(KEYSTORE_PASSWORD);

        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor wsSecurity = securing(signature(bodyReference()));
        wsSecurity.setKeyStore(keyStore);
        wsSecurity.init(router);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));

        assertSignatureIsValid(parseBody());
    }

    /** Signs the body with an otherwise pre-configured signature. */
    private Document signBodyWith(SignatureSecurePart signature) throws Exception {
        signature.setReferences(List.of(bodyReference()));
        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor wsSecurity = securing(signature);
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));
        wsSecurity.init(router);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
        return parseBody();
    }

    @Test
    void signsWithSecurityTokenReferenceKeyInfo() throws Exception {
        SignatureSecurePart signature = new SignatureSecurePart();
        signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        Document result = signBodyWith(signature);

        assertEquals(0, result.getElementsByTagNameNS(DS_NS, "X509Data").getLength());

        Element bst = firstByTag(result, WSSE_NS, "BinarySecurityToken");
        assertFalse(bst.getTextContent().isBlank());
        assertFalse(bst.getTextContent().matches("(?s).*\\s.*"));
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                bst.getAttribute("EncodingType"));
        String bstId = bst.getAttributeNS(WSU_NS, "Id");
        assertFalse(bstId.isEmpty());

        assertFalse(firstByTag(result, DS_NS, "KeyInfo").getAttribute("Id").isEmpty());
        assertFalse(firstByTag(result, WSSE_NS, "SecurityTokenReference").getAttributeNS(WSU_NS, "Id").isEmpty());
        assertEquals("#" + bstId, firstByTag(result, WSSE_NS, "Reference").getAttribute("URI"));

        // BST is not itself signed unless explicitly referenced via "by: BST".
        assertEquals(1, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertNotEquals("#" + bstId, firstByTag(result, DS_NS, "Reference").getAttribute("URI"));

        assertSignatureIsValid(result);
    }

    @Test
    void signsBinarySecurityTokenWhenReferenced() throws Exception {
        SignatureSecurePart signature = signature(bodyReference(), reference(SignatureReference.By.BST));
        signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor wsSecurity = signer(signature);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
        Document result = parseBody();

        Element bst = firstByTag(result, WSSE_NS, "BinarySecurityToken");
        String bstId = bst.getAttributeNS(WSU_NS, "Id");
        assertFalse(bstId.isEmpty());

        NodeList dsReferences = result.getElementsByTagNameNS(DS_NS, "Reference");
        assertEquals(2, dsReferences.getLength());
        boolean bstIsSigned = false;
        for (int i = 0; i < dsReferences.getLength(); i++) {
            if (("#" + bstId).equals(((Element) dsReferences.item(i)).getAttribute("URI"))) {
                bstIsSigned = true;
            }
        }
        assertTrue(bstIsSigned, "Expected a ds:Reference pointing at the BST's wsu:Id");

        // The wsse:Reference inside KeyInfo's SecurityTokenReference still points at the same BST.
        assertEquals("#" + bstId, firstByTag(result, WSSE_NS, "Reference").getAttribute("URI"));

        assertSignatureIsValid(result);
    }

    /**
     * A <code>wsse:Security</code> header nothing consumed is reused rather than replaced, so it can
     * still hold a token the peer sent. Both BST lookups take the first match while the generated
     * token is appended last, so a peer token left in place would be the one signed and the one
     * <code>ds:KeyInfo</code> points at - advertising the peer's certificate for a signature made
     * with the gateway key.
     */
    @Test
    void peerSuppliedBinarySecurityTokenIsNotSignedOrReferenced() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_PEER_BST);
        SignatureSecurePart signature = signature(bodyReference(), reference(SignatureReference.By.BST));
        signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        assertEquals(Outcome.CONTINUE, signer(signature).handleRequest(exchange));
        Document result = parseBody();

        // The peer's token is gone, and the one that remains carries the gateway certificate.
        Element bst = firstByTag(result, WSSE_NS, "BinarySecurityToken");
        assertNotEquals(PEER_CERTIFICATE, bst.getTextContent());
        assertEquals(Base64.getEncoder().encodeToString(certificate(ALIAS_1).getEncoded()), bst.getTextContent());

        String bstId = bst.getAttributeNS(WSU_NS, "Id");
        assertTrue(bstId.startsWith("X509-"));
        assertNotEquals("peer-token-1", bstId);
        // Both the ds:Reference that signs it and the wsse:Reference in KeyInfo point at ours.
        assertEquals("#" + bstId, firstByTag(result, WSSE_NS, "Reference").getAttribute("URI"));
        assertTrue(childElements(firstByTag(result, DS_NS, "SignedInfo")).stream()
                        .filter(e -> "Reference".equals(e.getLocalName()))
                        .anyMatch(e -> ("#" + bstId).equals(e.getAttribute("URI"))),
                "Expected a ds:Reference covering the generated BST");

        assertSignatureIsValid(result);
    }

    @Test
    void rejectsBstReferenceWithoutSecurityTokenReference() {
        // default KeyInfo mode (x509Data) - no BinarySecurityToken exists to reference.
        assertThrows(RuntimeException.class,
                () -> signer(signature(bodyReference(), reference(SignatureReference.By.BST))));
    }

    @Test
    void rejectsBothX509DataAndSecurityTokenReference() {
        SignatureSecurePart signature = signature(bodyReference());
        signature.setX509Data(new X509DataKeyInfo());
        signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        assertThrows(RuntimeException.class, () -> signer(signature));
    }

    @Test
    void rejectsSecurityTokenReferenceAndKeyIdentifier() {
        SignatureSecurePart signature = signature(bodyReference());
        signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        signature.setKeyIdentifier(new KeyIdentifierKeyInfo());

        assertThrows(RuntimeException.class, () -> signer(signature));
    }

    @Test
    void rejectsMissingKeyStore() {
        WsSecurityInterceptor wsSecurity = securing(signature(bodyReference()));

        assertThrows(RuntimeException.class, () -> wsSecurity.init(router));
    }

    @Test
    void rejectsSignatureWithoutReferences() {
        assertThrows(RuntimeException.class, () -> signer(new SignatureSecurePart()));
    }

    @Test
    void rejectsExplicitByCombinedWithXpath() {
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setBy(SignatureReference.By.XPATH);

        assertThrows(RuntimeException.class, () -> signer(signature(reference)));
    }

    @Test
    void rejectsNonXpathByCombinedWithXpath() {
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setBy(SignatureReference.By.BODY);

        assertThrows(RuntimeException.class, () -> signer(signature(reference)));
    }

    @Test
    void rejectsXpathByWithoutXpath() {
        assertThrows(RuntimeException.class,
                () -> signer(signature(reference(SignatureReference.By.XPATH))));
    }

    @Test
    void signsWithKeyIdentifierX509V3() throws Exception {
        Document result = signBodyWith(withKeyIdentifier(KeyIdentifierKeyInfo.ValueType.X509_V3));

        assertEquals(0, result.getElementsByTagNameNS(DS_NS, "X509Data").getLength());
        assertEquals(0, result.getElementsByTagNameNS(WSSE_NS, "BinarySecurityToken").getLength());

        Element keyIdentifier = firstByTag(result, WSSE_NS, "KeyIdentifier");
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3",
                keyIdentifier.getAttribute("ValueType"));
        assertFalse(keyIdentifier.getTextContent().isBlank());

        assertSignatureIsValid(result);
    }

    @Test
    void signsWithKeyIdentifierThumbprintSha1() throws Exception {
        Document result = signBodyWith(withKeyIdentifier(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1));

        Element keyIdentifier = firstByTag(result, WSSE_NS, "KeyIdentifier");
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.1#ThumbprintSHA1",
                keyIdentifier.getAttribute("ValueType"));

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        assertEquals(Base64.getEncoder().encodeToString(sha1.digest(certificate(ALIAS_1).getEncoded())),
                keyIdentifier.getTextContent());

        assertSignatureIsValid(result);
    }

    private static SignatureSecurePart withKeyIdentifier(KeyIdentifierKeyInfo.ValueType valueType) {
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(valueType);
        SignatureSecurePart signature = new SignatureSecurePart();
        signature.setKeyIdentifier(keyIdentifier);
        return signature;
    }

    @Test
    void invalidKeyAliasFailsAtInit() {
        WsSecurityInterceptor wsSecurity = securing(signature(bodyReference()));
        wsSecurity.setKeyStore(signingKeyStore("nonexistent"));

        assertThrows(RuntimeException.class, () -> wsSecurity.init(router));
    }

    @Test
    void rejectsUnsupportedDigestAlgorithm() {
        SignatureSecurePart signature = signature(bodyReference());
        signature.setDigestAlgorithm("bogus");

        assertTrue(assertThrows(RuntimeException.class, () -> signer(signature)).getMessage().contains("bogus"));
    }

    @Test
    void rejectsUnsupportedSignatureAlgorithm() {
        SignatureSecurePart signature = signature(bodyReference());
        signature.setSignatureAlgorithm("bogus");

        assertTrue(assertThrows(RuntimeException.class, () -> signer(signature)).getMessage().contains("bogus"));
    }

    @Test
    void rejectsMacSignatureAlgorithmAtInitRatherThanOnTheFirstMessage() {
        // The XMLSignatureFactory constructs an HMAC SignatureMethod happily, but signing here always
        // uses the keystore's PrivateKey, which no MAC can accept - so accepting the configuration
        // would only move the failure to the first message.
        SignatureSecurePart signature = signature(bodyReference());
        signature.setSignatureAlgorithm(SignatureMethod.HMAC_SHA1);

        assertTrue(assertThrows(RuntimeException.class, () -> signer(signature))
                .getMessage().contains("hmac-sha1"));
    }

    @Test
    void securityTokenReferenceCarriesTheWss11TokenType() throws Exception {
        // A ThumbprintSHA1 KeyIdentifier names the certificate by hash alone; WSS4J/CXF read TokenType
        // to learn what that hash identifies.
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1);
        SignatureSecurePart signature = signature(bodyReference());
        signature.setKeyIdentifier(keyIdentifier);

        exchangeWithBody(SOAP_BODY);
        assertEquals(Outcome.CONTINUE, signer(signature).handleRequest(exchange));

        Element str = firstByTag(parseBody(), WSSE_NS, "SecurityTokenReference");
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3",
                str.getAttributeNS(WSSE11_NS, "TokenType"));
    }

    @Test
    void anExistingUnqualifiedIdIsReusedWithoutAddingASecondIdAttribute() throws Exception {
        // Two ID attributes on one element holding the same value read as an ID collision to some
        // consumers, so the id that is already there is referenced as it stands.
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body Id="body-1"><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """);
        assertEquals(Outcome.CONTINUE, signer(signature(bodyReference())).handleRequest(exchange));

        Document result = parseBody();
        Element body = firstByTag(result, SOAP_NS, "Body");
        assertEquals("body-1", body.getAttribute("Id"));
        assertTrue(body.getAttributeNS(WSU_NS, "Id").isEmpty(), "Expected no second, wsu-qualified Id");
        assertEquals("#body-1", firstByTag(result, DS_NS, "Reference").getAttribute("URI"));
    }

    @Test
    void rejectsUnsupportedCanonicalizationAlgorithm() {
        SignatureSecurePart signature = signature(bodyReference());
        signature.setCanonicalizationAlgorithm("bogus");

        assertTrue(assertThrows(RuntimeException.class, () -> signer(signature)).getMessage().contains("bogus"));
    }

    /**
     * Every advertised canonicalization algorithm has to survive an actual signing run, not just
     * init: the inclusive ones take no ExcC14NParameterSpec, so a spec applied unconditionally
     * would let a configuration pass validation and then fail on the first message.
     */
    @ParameterizedTest
    @ValueSource(strings = {CanonicalizationMethod.INCLUSIVE, CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
            CanonicalizationMethod.EXCLUSIVE, CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS})
    void signsWithEverySupportedCanonicalizationAlgorithm(String algorithm) throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureSecurePart signature = signature(bodyReference());
        signature.setCanonicalizationAlgorithm(algorithm);

        assertEquals(Outcome.CONTINUE, signer(signature).handleRequest(exchange));

        Document result = parseBody();
        assertEquals(algorithm,
                firstByTag(result, DS_NS, "CanonicalizationMethod").getAttribute("Algorithm"));
        assertSignatureIsValid(result);
    }

    @Test
    void nonSoapMessageAborts() throws Exception {
        exchangeWithBody("<foo>bar</foo>");

        assertAborts(signer(signature(bodyReference())), 400);
    }

    @Test
    void defaultAlgorithmsAreApplied() throws Exception {
        Document result = signBodyAndParse();

        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
                firstByTag(result, DS_NS, "SignatureMethod").getAttribute("Algorithm"));
        assertEquals(EXC_C14N_NS,
                firstByTag(result, DS_NS, "CanonicalizationMethod").getAttribute("Algorithm"));
    }

    /**
     * A wsu:Timestamp this same secure list just created has no xmlns:wsu declaration of its own -
     * only a prefix. Canonicalization at signing time sees no declaration to emit, while the
     * serializer adds one on the way out, so the receiver digests different bytes than were signed.
     * Only a real serialize/re-parse round trip catches that.
     */
    @Test
    void referenceToAFreshlyCreatedTimestampSurvivesSerialization() throws Exception {
        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor wsSecurity = securing(
                new TimestampSecurePart(), signature(bodyReference(), reference(SignatureReference.By.TIMESTAMP)));
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));
        wsSecurity.init(router);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));

        assertSignatureIsValid(parseBody());
    }
}
