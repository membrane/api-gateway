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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import java.security.cert.Certificate;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class DigitalSignatureInterceptorTest {

    private static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String EXC_C14N_NS = "http://www.w3.org/2001/10/xml-exc-c14n#";
    private static final String KEYSTORE_PASSWORD = "secret";
    private static final String ALIAS = "key1";

    private static final String SOAP_BODY = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    private static final String SOAP_BODY_WITH_ID = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                            xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                <soap:Body wsu:Id="existing-1">
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    DefaultRouter router;
    Exchange exchange;
    DigitalSignatureInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
        interceptor = new DigitalSignatureInterceptor();
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation("classpath:/alias-keystore.p12");
        keyStore.setKeyPassword(KEYSTORE_PASSWORD);
        keyStore.setKeyAlias(ALIAS);
        interceptor.setKeyStore(keyStore);
    }

    private void exchangeWithBody(String body) throws Exception {
        exchange = new Exchange(null);
        exchange.setRequest(new Request.Builder()
                .post("/service")
                .contentType(TEXT_XML)
                .body(body)
                .build());
    }

    private Document parseResultBody() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(exchange.getRequest().getBodyAsStream());
    }

    private static Element firstByTag(Document doc, String namespace, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(namespace, localName);
        assertEquals(1, nodes.getLength(), "Expected exactly one " + localName + " element");
        return (Element) nodes.item(0);
    }

    private void assertSignatureIsValid(Document doc) throws Exception {
        Element signatureElement = firstByTag(doc, DS_NS, "Signature");

        // Re-mark every wsu:Id-bearing element as an XML ID attribute, since that information
        // is lost when the document is freshly re-parsed for this assertion.
        NodeList allElements = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element el && !el.getAttributeNS(WSU_NS, "Id").isEmpty()) {
                el.setIdAttributeNS(WSU_NS, "Id", true);
            }
        }

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext valContext = new DOMValidateContext(certificate().getPublicKey(), signatureElement);
        XMLSignature signature = fac.unmarshalXMLSignature(valContext);
        assertTrue(signature.validate(valContext), "Signature must validate against the certificate's public key");
    }

    private Certificate certificate() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (var is = getClass().getResourceAsStream("/alias-keystore.p12")) {
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());
        }
        return ks.getCertificate(ALIAS);
    }

    @Test
    void signsBodyByDefault() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element security = firstByTag(result, WSSE_NS, "Security");
        assertNotNull(security);
        Element signature = firstByTag(result, DS_NS, "Signature");
        assertNotNull(signature);

        Element body = firstByTag(result, "http://schemas.xmlsoap.org/soap/envelope/", "Body");
        String bodyId = body.getAttributeNS(WSU_NS, "Id");
        assertFalse(bodyId.isEmpty());

        Element reference = firstByTag(result, DS_NS, "Reference");
        assertEquals("#" + bodyId, reference.getAttribute("URI"));

        Element x509Cert = firstByTag(result, DS_NS, "X509Certificate");
        assertFalse(x509Cert.getTextContent().isBlank());
        Element signatureValue = firstByTag(result, DS_NS, "SignatureValue");
        assertFalse(signatureValue.getTextContent().isBlank());
    }

    @Test
    void signatureIsCryptographicallyVerifiable() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        assertSignatureIsValid(parseResultBody());
    }

    @Test
    void multipleReferencesAreAllSigned() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference bodyRef = new SignatureReference();
        bodyRef.setBy(SignatureReference.By.BODY);
        SignatureReference xpathRef = new SignatureReference();
        xpathRef.setBy(SignatureReference.By.XPATH);
        xpathRef.setXpath("//*[local-name()='foo']");
        interceptor.setReferences(List.of(bodyRef, xpathRef));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        NodeList references = result.getElementsByTagNameNS(DS_NS, "Reference");
        assertEquals(2, references.getLength());

        assertSignatureIsValid(result);
    }

    @Test
    void signatureElementHasIdAndInclusiveNamespaces() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element signature = firstByTag(result, DS_NS, "Signature");
        assertTrue(signature.getAttribute("Id").startsWith("SIG-"));

        Element canonicalizationMethod = firstByTag(result, DS_NS, "CanonicalizationMethod");
        Element c14nInclusiveNamespaces = (Element) canonicalizationMethod
                .getElementsByTagNameNS(EXC_C14N_NS, "InclusiveNamespaces").item(0);
        assertNotNull(c14nInclusiveNamespaces);
        assertEquals("soap", c14nInclusiveNamespaces.getAttribute("PrefixList"));

        Element transform = firstByTag(result, DS_NS, "Transform");
        Element transformInclusiveNamespaces = (Element) transform
                .getElementsByTagNameNS(EXC_C14N_NS, "InclusiveNamespaces").item(0);
        assertNotNull(transformInclusiveNamespaces);
        assertEquals("", transformInclusiveNamespaces.getAttribute("PrefixList"));

        assertSignatureIsValid(result);
    }

    @Test
    void securityHeaderGetsMustUnderstand() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element security = firstByTag(result, WSSE_NS, "Security");
        assertEquals("1", security.getAttributeNS("http://schemas.xmlsoap.org/soap/envelope/", "mustUnderstand"));
    }

    @Test
    void existingWsuIdIsReusedNotOverwritten() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_ID);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element reference = firstByTag(result, DS_NS, "Reference");
        assertEquals("#existing-1", reference.getAttribute("URI"));
    }

    @Test
    void xpathMatchingZeroElementsAborts() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.XPATH);
        ref.setXpath("//*[local-name()='doesNotExist']");
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void xpathMatchingMultipleElementsAborts() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        <foo>bar</foo>
                        <foo>baz</foo>
                    </soap:Body>
                </soap:Envelope>
                """);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.XPATH);
        ref.setXpath("//*[local-name()='foo']");
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void passwordIsUsedAsKeyPasswordFallback() throws Exception {
        // Only `password` set, no distinct `keyPassword` - common for a PKCS12 keystore where
        // both are the same.
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation("classpath:/alias-keystore.p12");
        keyStore.setPassword(KEYSTORE_PASSWORD);
        keyStore.setKeyAlias(ALIAS);
        interceptor.setKeyStore(keyStore);

        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));
        assertSignatureIsValid(parseResultBody());
    }

    @Test
    void signsWithSecurityTokenReferenceKeyInfo() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        assertEquals(0, result.getElementsByTagNameNS(DS_NS, "X509Data").getLength());

        Element bst = firstByTag(result, WSSE_NS, "BinarySecurityToken");
        assertFalse(bst.getTextContent().isBlank());
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                bst.getAttribute("EncodingType"));
        String bstId = bst.getAttributeNS(WSU_NS, "Id");
        assertFalse(bstId.isEmpty());

        Element keyInfo = firstByTag(result, DS_NS, "KeyInfo");
        assertFalse(keyInfo.getAttribute("Id").isEmpty());

        Element str = firstByTag(result, WSSE_NS, "SecurityTokenReference");
        assertFalse(str.getAttributeNS(WSU_NS, "Id").isEmpty());

        Element strReference = firstByTag(result, WSSE_NS, "Reference");
        assertEquals("#" + bstId, strReference.getAttribute("URI"));

        assertSignatureIsValid(result);
    }

    @Test
    void rejectsBothX509DataAndSecurityTokenReference() {
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.setX509Data(new X509DataKeyInfo());
        interceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        assertThrows(RuntimeException.class, () -> interceptor.init(router));
    }

    @Test
    void rejectsSecurityTokenReferenceAndKeyIdentifier() {
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        interceptor.setKeyIdentifier(new KeyIdentifierKeyInfo());

        assertThrows(RuntimeException.class, () -> interceptor.init(router));
    }

    @Test
    void signsWithKeyIdentifierX509V3() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(KeyIdentifierKeyInfo.ValueType.X509_V3);
        interceptor.setKeyIdentifier(keyIdentifier);
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        assertEquals(0, result.getElementsByTagNameNS(DS_NS, "X509Data").getLength());
        assertEquals(0, result.getElementsByTagNameNS(WSSE_NS, "BinarySecurityToken").getLength());

        Element keyIdentifierEl = firstByTag(result, WSSE_NS, "KeyIdentifier");
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3",
                keyIdentifierEl.getAttribute("ValueType"));
        assertFalse(keyIdentifierEl.getTextContent().isBlank());

        assertSignatureIsValid(result);
    }

    @Test
    void signsWithKeyIdentifierThumbprintSha1() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1);
        interceptor.setKeyIdentifier(keyIdentifier);
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element keyIdentifierEl = firstByTag(result, WSSE_NS, "KeyIdentifier");
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.1#ThumbprintSHA1",
                keyIdentifierEl.getAttribute("ValueType"));

        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
        String expectedThumbprint = java.util.Base64.getEncoder().encodeToString(sha1.digest(certificate().getEncoded()));
        assertEquals(expectedThumbprint, keyIdentifierEl.getTextContent());

        assertSignatureIsValid(result);
    }

    @Test
    void invalidKeyAliasFailsAtInit() {
        interceptor.getKeyStore().setKeyAlias("nonexistent");
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));

        assertThrows(RuntimeException.class, () -> interceptor.init(router));
    }

    @Test
    void nonSoapMessageAborts() throws Exception {
        exchangeWithBody("<foo>bar</foo>");
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void defaultAlgorithmsAreApplied() throws Exception {
        exchangeWithBody(SOAP_BODY);
        SignatureReference ref = new SignatureReference();
        ref.setBy(SignatureReference.By.BODY);
        interceptor.setReferences(List.of(ref));
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element signatureMethod = firstByTag(result, DS_NS, "SignatureMethod");
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", signatureMethod.getAttribute("Algorithm"));
        Element canonicalizationMethod = firstByTag(result, DS_NS, "CanonicalizationMethod");
        assertEquals("http://www.w3.org/2001/10/xml-exc-c14n#", canonicalizationMethod.getAttribute("Algorithm"));
    }
}
