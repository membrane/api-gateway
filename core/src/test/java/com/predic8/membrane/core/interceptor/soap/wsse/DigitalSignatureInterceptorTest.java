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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
    void signatureIsCryptographicallyVerifiable() throws Exception {
        assertSignatureIsValid(signBodyAndParse());
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
        assertEquals("", inclusiveNamespacesPrefixList(firstByTag(result, DS_NS, "Transform")));

        assertSignatureIsValid(result);
    }

    @Test
    void securityHeaderGetsMustUnderstand() throws Exception {
        Element security = firstByTag(signBodyAndParse(), WSSE_NS, "Security");

        assertEquals("1", security.getAttributeNS(SOAP_NS, "mustUnderstand"));
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
        initWith(xpathReference("//*[local-name()='foo']"));

        assertAborts(interceptor, 400);
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
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                bst.getAttribute("EncodingType"));
        String bstId = bst.getAttributeNS(WSU_NS, "Id");
        assertFalse(bstId.isEmpty());

        assertFalse(firstByTag(result, DS_NS, "KeyInfo").getAttribute("Id").isEmpty());
        assertFalse(firstByTag(result, WSSE_NS, "SecurityTokenReference").getAttributeNS(WSU_NS, "Id").isEmpty());
        assertEquals("#" + bstId, firstByTag(result, WSSE_NS, "Reference").getAttribute("URI"));

        assertSignatureIsValid(result);
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
