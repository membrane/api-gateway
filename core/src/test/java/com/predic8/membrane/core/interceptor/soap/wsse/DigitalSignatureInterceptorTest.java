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
import com.predic8.membrane.core.config.xml.Namespaces;
import com.predic8.membrane.core.config.xml.XmlConfig;
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class DigitalSignatureInterceptorTest extends AbstractWsseInterceptorTest {

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

    DigitalSignatureInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DigitalSignatureInterceptor();
        interceptor.setKeyStore(signingKeyStore(ALIAS_1));
    }

    private void initWith(SignatureReference... references) {
        interceptor.setReferences(List.of(references));
        interceptor.init(router);
    }

    private Document signAndParse(String body, SignatureReference... references) throws Exception {
        exchangeWithBody(body);
        initWith(references);
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));
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

    @Test
    void securityIsInsertedAsFirstHeader() throws Exception {
        Document result = signAndParse(SOAP_BODY_WITH_EXISTING_HEADER, bodyReference());

        Element header = firstByTag(result, SOAP_NS, "Header");
        assertEquals(WSSE_NS, header.getFirstChild().getNamespaceURI());
        assertEquals("Security", header.getFirstChild().getLocalName());
    }

    @Test
    void existingWsuIdIsReusedNotOverwritten() throws Exception {
        Document result = signAndParse(SOAP_BODY_WITH_ID, bodyReference());

        assertEquals("#existing-1", firstByTag(result, DS_NS, "Reference").getAttribute("URI"));
    }

    @Test
    void xpathMatchingZeroElementsAborts() throws Exception {
        exchangeWithBody(SOAP_BODY);
        initWith(xpathReference("//*[local-name()='doesNotExist']"));

        assertAborts(interceptor, 400);
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
    void explicitIdWithMultipleXPathMatchesAborts() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TWO_FOOS);
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setId("explicit-id");
        initWith(reference);

        assertAborts(interceptor, 400);
    }

    @Test
    void xpathWithBuiltInSoapPrefixResolvesWithoutXmlConfig() throws Exception {
        Document result = signAndParse(SOAP_BODY, xpathReference("//soap:Body"));

        assertEquals(1, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    @Test
    void xpathWithCustomPrefixResolvesViaXmlConfig() throws Exception {
        interceptor.setXmlConfig(xmlConfig("c", "https://predic8.de/custom"));

        Document result = signAndParse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        <c:foo xmlns:c="https://predic8.de/custom">bar</c:foo>
                    </soap:Body>
                </soap:Envelope>
                """, xpathReference("//c:foo"));

        assertEquals(1, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    @Test
    void xmlConfigMergesCustomPrefixesWithBuiltIns() throws Exception {
        interceptor.setXmlConfig(xmlConfig("c", "https://predic8.de/custom"));

        Document result = signAndParse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        <c:foo xmlns:c="https://predic8.de/custom">bar</c:foo>
                    </soap:Body>
                </soap:Envelope>
                """, xpathReference("//soap:Body/c:foo"));

        assertEquals(1, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
        assertSignatureIsValid(result);
    }

    private static XmlConfig xmlConfig(String prefix, String uri) {
        Namespaces.Namespace namespace = new Namespaces.Namespace();
        namespace.setPrefix(prefix);
        namespace.setUri(uri);
        Namespaces namespaces = new Namespaces();
        namespaces.setNamespaces(List.of(namespace));
        XmlConfig xmlConfig = new XmlConfig();
        xmlConfig.setNamespaces(namespaces);
        return xmlConfig;
    }

    @Test
    void passwordIsUsedAsKeyPasswordFallback() throws Exception {
        // Only `password` set, no distinct `keyPassword` - common for a PKCS12 keystore where both
        // are the same.
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation(KEYSTORE);
        keyStore.setKeyAlias(ALIAS_1);
        keyStore.setPassword(KEYSTORE_PASSWORD);
        interceptor.setKeyStore(keyStore);

        assertSignatureIsValid(signBodyAndParse());
    }

    @Test
    void signsWithSecurityTokenReferenceKeyInfo() throws Exception {
        interceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        Document result = signBodyAndParse();

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
        interceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        Document result = signAndParse(SOAP_BODY, bodyReference(), reference(SignatureReference.By.BST));

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

    @Test
    void rejectsBstReferenceWithoutSecurityTokenReference() {
        // default KeyInfo mode (x509Data) - no BinarySecurityToken exists to reference.
        assertThrows(RuntimeException.class,
                () -> initWith(bodyReference(), reference(SignatureReference.By.BST)));
    }

    @Test
    void rejectsBothX509DataAndSecurityTokenReference() {
        interceptor.setX509Data(new X509DataKeyInfo());
        interceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        assertThrows(RuntimeException.class, () -> initWith(bodyReference()));
    }

    @Test
    void rejectsSecurityTokenReferenceAndKeyIdentifier() {
        interceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        interceptor.setKeyIdentifier(new KeyIdentifierKeyInfo());

        assertThrows(RuntimeException.class, () -> initWith(bodyReference()));
    }

    @Test
    void rejectsExplicitByCombinedWithXpath() {
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setBy(SignatureReference.By.XPATH);

        assertThrows(RuntimeException.class, () -> initWith(reference));
    }

    @Test
    void rejectsNonXpathByCombinedWithXpath() {
        SignatureReference reference = xpathReference("//*[local-name()='foo']");
        reference.setBy(SignatureReference.By.BODY);

        assertThrows(RuntimeException.class, () -> initWith(reference));
    }

    @Test
    void rejectsXpathByWithoutXpath() {
        assertThrows(RuntimeException.class, () -> initWith(reference(SignatureReference.By.XPATH)));
    }

    @Test
    void signsWithKeyIdentifierX509V3() throws Exception {
        useKeyIdentifier(KeyIdentifierKeyInfo.ValueType.X509_V3);

        Document result = signBodyAndParse();

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
        useKeyIdentifier(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1);

        Document result = signBodyAndParse();

        Element keyIdentifier = firstByTag(result, WSSE_NS, "KeyIdentifier");
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.1#ThumbprintSHA1",
                keyIdentifier.getAttribute("ValueType"));

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        assertEquals(Base64.getEncoder().encodeToString(sha1.digest(certificate(ALIAS_1).getEncoded())),
                keyIdentifier.getTextContent());

        assertSignatureIsValid(result);
    }

    private void useKeyIdentifier(KeyIdentifierKeyInfo.ValueType valueType) {
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(valueType);
        interceptor.setKeyIdentifier(keyIdentifier);
    }

    @Test
    void invalidKeyAliasFailsAtInit() {
        interceptor.getKeyStore().setKeyAlias("nonexistent");

        assertThrows(RuntimeException.class, () -> initWith(bodyReference()));
    }

    @Test
    void rejectsUnsupportedDigestAlgorithm() {
        interceptor.setDigestAlgorithm("bogus");

        RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(bodyReference()));
        assertTrue(e.getMessage().contains("bogus"));
    }

    @Test
    void rejectsUnsupportedSignatureAlgorithm() {
        interceptor.setSignatureAlgorithm("bogus");

        RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(bodyReference()));
        assertTrue(e.getMessage().contains("bogus"));
    }

    @Test
    void rejectsUnsupportedCanonicalizationAlgorithm() {
        interceptor.setCanonicalizationAlgorithm("bogus");

        RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(bodyReference()));
        assertTrue(e.getMessage().contains("bogus"));
    }

    @Test
    void nonSoapMessageAborts() throws Exception {
        exchangeWithBody("<foo>bar</foo>");
        initWith(bodyReference());

        assertAborts(interceptor, 400);
    }

    @Test
    void defaultAlgorithmsAreApplied() throws Exception {
        Document result = signBodyAndParse();

        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
                firstByTag(result, DS_NS, "SignatureMethod").getAttribute("Algorithm"));
        assertEquals(EXC_C14N_NS,
                firstByTag(result, DS_NS, "CanonicalizationMethod").getAttribute("Algorithm"));
    }
}
