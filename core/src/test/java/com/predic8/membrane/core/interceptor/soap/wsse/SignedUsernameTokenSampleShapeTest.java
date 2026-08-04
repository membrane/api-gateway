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

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.security.cert.Certificate;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that chaining {@code usernameToken} and {@code digitalSignature} (with
 * {@code securityTokenReference}) reproduces the WS-Security shape a CXF/WSS4J-based service
 * commonly produces: a signed {@code wsse:UsernameToken}, with the signing certificate referenced
 * via a {@code wsse:BinarySecurityToken} + {@code wsse:SecurityTokenReference} rather than inlined,
 * plus the {@code ds:Signature}/{@code ds:KeyInfo} {@code Id} attributes, the
 * {@code ec:InclusiveNamespaces} prefix lists, and {@code mustUnderstand} that such an
 * implementation typically emits. All values here (username, password, certificate) are synthetic.
 */
class SignedUsernameTokenSampleShapeTest {

    private static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String EXC_C14N_NS = "http://www.w3.org/2001/10/xml-exc-c14n#";
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String KEYSTORE_PASSWORD = "secret";
    private static final String ALIAS = "key1";

    // Mirrors the shape of the pasted sample (soapenv prefix, an Example element in the body)
    // but with entirely synthetic content.
    private static final String SOAP_BODY = """
            <soapenv:Envelope xmlns:soapenv="%s">
                <soapenv:Body>
                    <Example>ACME</Example>
                </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAP_NS);

    DefaultRouter router;
    Exchange exchange;
    UsernameTokenInterceptor usernameTokenInterceptor;
    DigitalSignatureInterceptor digitalSignatureInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        router = new DefaultRouter();

        usernameTokenInterceptor = new UsernameTokenInterceptor();
        usernameTokenInterceptor.setUsername("ACME-001");
        usernameTokenInterceptor.setPassword("Example123!");
        usernameTokenInterceptor.init(router);

        KeyStore keyStore = new KeyStore();
        keyStore.setLocation("classpath:/alias-keystore.p12");
        keyStore.setKeyPassword(KEYSTORE_PASSWORD);
        keyStore.setKeyAlias(ALIAS);

        digitalSignatureInterceptor = new DigitalSignatureInterceptor();
        digitalSignatureInterceptor.setKeyStore(keyStore);
        digitalSignatureInterceptor.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
        SignatureReference reference = new SignatureReference();
        reference.setBy(SignatureReference.By.XPATH);
        reference.setXpath("//*[local-name()='UsernameToken']");
        digitalSignatureInterceptor.setReferences(List.of(reference));
        digitalSignatureInterceptor.init(router);

        exchange = new Exchange(null);
        exchange.setRequest(new Request.Builder()
                .post("/service")
                .contentType(TEXT_XML)
                .body(SOAP_BODY)
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

    private Certificate certificate() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (var is = getClass().getResourceAsStream("/alias-keystore.p12")) {
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());
        }
        return ks.getCertificate(ALIAS);
    }

    @Test
    void producesTheExpectedCxfStyleShape() throws Exception {
        assertEquals(Outcome.CONTINUE, usernameTokenInterceptor.handleRequest(exchange));
        assertEquals(Outcome.CONTINUE, digitalSignatureInterceptor.handleRequest(exchange));

        Document result = parseResultBody();

        // wsse:Security carries mustUnderstand.
        Element security = firstByTag(result, WSSE_NS, "Security");
        assertEquals("1", security.getAttributeNS(SOAP_NS, "mustUnderstand"));

        // wsse:UsernameToken with the synthetic credentials, signed by reference.
        Element usernameToken = firstByTag(result, WSSE_NS, "UsernameToken");
        assertEquals("ACME-001", firstByTag(result, WSSE_NS, "Username").getTextContent());
        Element password = firstByTag(result, WSSE_NS, "Password");
        assertEquals("Example123!", password.getTextContent());
        assertEquals(WSSE_NS + "#PasswordText", password.getAttribute("Type"));
        String usernameTokenId = usernameToken.getAttributeNS(WSU_NS, "Id");
        assertFalse(usernameTokenId.isEmpty());

        // ds:Signature has an Id, and its single Reference points at the UsernameToken.
        Element signature = firstByTag(result, DS_NS, "Signature");
        assertTrue(signature.getAttribute("Id").startsWith("SIG-"));
        Element reference = firstByTag(result, DS_NS, "Reference");
        assertEquals("#" + usernameTokenId, reference.getAttribute("URI"));

        // SignedInfo: exclusive c14n with InclusiveNamespaces="soapenv", RSA-SHA256, SHA-256 digest,
        // and an empty InclusiveNamespaces on the per-reference transform.
        Element canonicalizationMethod = firstByTag(result, DS_NS, "CanonicalizationMethod");
        assertEquals(EXC_C14N_NS, canonicalizationMethod.getAttribute("Algorithm"));
        Element signedInfoInclusiveNamespaces = (Element) canonicalizationMethod
                .getElementsByTagNameNS(EXC_C14N_NS, "InclusiveNamespaces").item(0);
        assertEquals("soapenv", signedInfoInclusiveNamespaces.getAttribute("PrefixList"));

        Element signatureMethod = firstByTag(result, DS_NS, "SignatureMethod");
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", signatureMethod.getAttribute("Algorithm"));
        Element digestMethod = firstByTag(result, DS_NS, "DigestMethod");
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha256", digestMethod.getAttribute("Algorithm"));

        Element transform = firstByTag(result, DS_NS, "Transform");
        Element transformInclusiveNamespaces = (Element) transform
                .getElementsByTagNameNS(EXC_C14N_NS, "InclusiveNamespaces").item(0);
        assertEquals("", transformInclusiveNamespaces.getAttribute("PrefixList"));

        // ds:KeyInfo/wsse:SecurityTokenReference/wsse:Reference points at the BinarySecurityToken,
        // instead of an inline ds:X509Data.
        assertEquals(0, result.getElementsByTagNameNS(DS_NS, "X509Data").getLength());
        Element keyInfo = firstByTag(result, DS_NS, "KeyInfo");
        assertTrue(keyInfo.getAttribute("Id").startsWith("KI-"));

        Element securityTokenReference = firstByTag(result, WSSE_NS, "SecurityTokenReference");
        assertTrue(securityTokenReference.getAttributeNS(WSU_NS, "Id").startsWith("STR-"));

        Element strReference = firstByTag(result, WSSE_NS, "Reference");
        String x509ValueType = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";
        assertEquals(x509ValueType, strReference.getAttribute("ValueType"));

        Element binarySecurityToken = firstByTag(result, WSSE_NS, "BinarySecurityToken");
        String tokenId = binarySecurityToken.getAttributeNS(WSU_NS, "Id");
        assertTrue(tokenId.startsWith("X509-"));
        assertEquals("#" + tokenId, strReference.getAttribute("URI"));
        assertEquals(x509ValueType, binarySecurityToken.getAttribute("ValueType"));
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                binarySecurityToken.getAttribute("EncodingType"));
        assertFalse(binarySecurityToken.getTextContent().isBlank());

        // And the signature actually verifies against the certificate embedded via the STR/BST.
        NodeList allElements = result.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element el && !el.getAttributeNS(WSU_NS, "Id").isEmpty()) {
                el.setIdAttributeNS(WSU_NS, "Id", true);
            }
        }
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext valContext = new DOMValidateContext(certificate().getPublicKey(), signature);
        XMLSignature xmlSignature = fac.unmarshalXMLSignature(valContext);
        assertTrue(xmlSignature.validate(valContext), "Signature must validate against the certificate's public key");
    }
}
