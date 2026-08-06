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
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.util.xml.XMLUtil;
import org.junit.jupiter.api.BeforeEach;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.security.cert.Certificate;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared scaffolding for the WS-Security interceptor tests: exchange construction, namespace-aware
 * parsing of the resulting body, keystore/certificate access and signature validation.
 */
abstract class AbstractWsseInterceptorTest {

    static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";
    static final String EXC_C14N_NS = "http://www.w3.org/2001/10/xml-exc-c14n#";

    static final String KEYSTORE_PASSWORD = "secret";
    static final String ALIAS_1 = "key1";
    static final String ALIAS_2 = "key2";
    static final String KEYSTORE = "classpath:/alias-keystore.p12";
    static final String TRUSTSTORE = "classpath:/alias-truststore.p12";
    static final String TRUSTSTORE_KEY2 = "classpath:/alias-truststore2.p12";

    static final String SOAP_BODY = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    DefaultRouter router;
    Exchange exchange;

    @BeforeEach
    void createRouter() {
        router = new DefaultRouter();
    }

    void exchangeWithBody(String body) throws Exception {
        exchange = new Exchange(null);
        exchange.setRequest(new Request.Builder()
                .post("/service")
                .contentType(TEXT_XML)
                .body(body)
                .build());
    }

    Document parseBody() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(exchange.getRequest().getBodyAsStream());
    }

    String rawBody() throws Exception {
        return exchange.getRequest().getBodyAsStringDecoded();
    }

    void setBody(Document doc) throws Exception {
        exchange.getRequest().setBodyContent(XMLUtil.xmlNode2String(doc).getBytes(UTF_8));
    }

    /**
     * Asserts the interceptor aborted the exchange with the given status code.
     */
    void assertAborts(Interceptor interceptor, int expectedStatusCode) throws Exception {
        assertEquals(Outcome.ABORT, interceptor.handleRequest(exchange));
        assertEquals(expectedStatusCode, exchange.getResponse().getStatusCode());
    }

    static Element firstByTag(Document doc, String namespace, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(namespace, localName);
        assertEquals(1, nodes.getLength(), "Expected exactly one " + localName + " element");
        return (Element) nodes.item(0);
    }

    static String inclusiveNamespacesPrefixList(Element parent) {
        Element inclusiveNamespaces = (Element) parent
                .getElementsByTagNameNS(EXC_C14N_NS, "InclusiveNamespaces").item(0);
        assertNotNull(inclusiveNamespaces, "Expected an ec:InclusiveNamespaces below " + parent.getLocalName());
        return inclusiveNamespaces.getAttribute("PrefixList");
    }

    static void assertNoInclusiveNamespaces(Element parent) {
        assertEquals(0, parent.getElementsByTagNameNS(EXC_C14N_NS, "InclusiveNamespaces").getLength(),
                "Expected no ec:InclusiveNamespaces below " + parent.getLocalName());
    }

    static KeyStore signingKeyStore(String alias) {
        KeyStore keyStore = new KeyStore();
        keyStore.setLocation(KEYSTORE);
        keyStore.setKeyAlias(alias);
        keyStore.setKeyPassword(KEYSTORE_PASSWORD);
        return keyStore;
    }

    static SignatureReference reference(SignatureReference.By by) {
        SignatureReference ref = new SignatureReference();
        ref.setBy(by);
        return ref;
    }

    static SignatureReference bodyReference() {
        return reference(SignatureReference.By.BODY);
    }

    static SignatureReference xpathReference(String xpath) {
        SignatureReference ref = new SignatureReference();
        ref.setXpath(xpath);
        return ref;
    }

    Certificate certificate(String alias) throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (var is = getClass().getResourceAsStream("/alias-keystore.p12")) {
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());
        }
        return ks.getCertificate(alias);
    }

    void assertSignatureIsValid(Document doc) throws Exception {
        Element signatureElement = firstByTag(doc, DS_NS, "Signature");
        markWsuIdsAsXmlIds(doc);

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext valContext = new DOMValidateContext(certificate(ALIAS_1).getPublicKey(), signatureElement);
        XMLSignature signature = fac.unmarshalXMLSignature(valContext);
        assertTrue(signature.validate(valContext), "Signature must validate against the certificate's public key");
    }

    /**
     * Re-marks every wsu:Id-bearing element as an XML ID attribute, since that information is lost
     * when the document is freshly re-parsed for an assertion.
     */
    private static void markWsuIdsAsXmlIds(Document doc) {
        NodeList allElements = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element el && !el.getAttributeNS(WSU_NS, "Id").isEmpty()) {
                el.setIdAttributeNS(WSU_NS, "Id", true);
            }
        }
    }
}
