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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the exact wire shape of what {@code encrypt} emits, against what Apache WSS4J/CXF produce for
 * the same policy.
 * <p>
 * Every other test here has Membrane on both ends, so a self-consistent mistake - the wrong
 * namespace on an algorithm URI, the {@code EncryptionMethod} children in the wrong order, a missing
 * {@code xenc11:MGF} - round-trips perfectly and proves nothing about a real peer. This is the
 * closest thing to interop coverage available without taking on a WSS4J dependency, and it fails at
 * build time rather than during an integration against someone else's stack.
 * <p>
 * The counterpart of {@link SignedUsernameTokenSampleShapeTest} on the confidentiality side.
 */
class EncryptedKeySampleShapeTest extends AbstractWsSecurityTest {

    private Document encrypted;

    @BeforeEach
    void encryptTheBody() throws Exception {
        exchangeWithBody(SOAP_BODY);
        encrypter(TRUSTSTORE, encrypt(ALIAS_1, encryptedBodyReference())).handleRequest(exchange);
        encrypted = parseBody();
    }

    private static List<String> childNames(Element parent) {
        List<String> names = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                names.add(element.getNodeName());
            }
        }
        return names;
    }

    private Element encryptedKey() {
        return firstByTag(encrypted, XENC_NS, "EncryptedKey");
    }

    /**
     * Element names live in the 2001 XML Encryption namespace even in XML Encryption 1.1; only the
     * newer algorithm URIs moved. Getting this split wrong is silent, because both are real
     * namespaces and nothing rejects the combination locally.
     */
    @Test
    void elementNamesAreInTheXmlEncryption10Namespace() {
        assertEquals("http://www.w3.org/2001/04/xmlenc#", XENC_NS);
        for (String localName : List.of("EncryptedKey", "EncryptedData", "EncryptionMethod",
                "CipherData", "CipherValue", "ReferenceList", "DataReference")) {
            assertTrue(encrypted.getElementsByTagNameNS(XENC_NS, localName).getLength() >= 1,
                    "expected an xenc:" + localName + " in the 2001/04 namespace");
        }
    }

    @Test
    void algorithmUrisAreInTheXmlEncryption11Namespace() {
        assertEquals("http://www.w3.org/2009/xmlenc11#", XENC11_NS);
        assertEquals("http://www.w3.org/2009/xmlenc11#aes256-gcm", AES256_GCM);
        assertEquals("http://www.w3.org/2009/xmlenc11#aes128-gcm", AES128_GCM);
        assertEquals("http://www.w3.org/2009/xmlenc11#rsa-oaep", RSA_OAEP);
        assertEquals("http://www.w3.org/2009/xmlenc11#mgf1sha256", MGF1_SHA256);
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha256", SHA256_DIGEST);
    }

    /**
     * WSS4J writes {@code ds:DigestMethod} before {@code xenc11:MGF}, and that is also the order
     * {@code EncryptionMethodType}'s content model implies.
     */
    @Test
    void theKeyTransportEncryptionMethodHasItsChildrenInWss4jOrder() {
        Element method = getFirstChildByName(encryptedKey(), XENC_NS, "EncryptionMethod");

        assertEquals(RSA_OAEP, method.getAttribute("Algorithm"));
        assertEquals(List.of("ds:DigestMethod", "xenc11:MGF"), childNames(method));
        assertEquals(SHA256_DIGEST,
                getFirstChildByName(method, DS_NS, "DigestMethod").getAttribute("Algorithm"));
        assertEquals(MGF1_SHA256,
                getFirstChildByName(method, XENC11_NS, "MGF").getAttribute("Algorithm"));
    }

    /**
     * The 1.1 {@code rsa-oaep} URI, not the 1.0 {@code rsa-oaep-mgf1p}: the latter bakes MGF1-SHA-1
     * into the URI, so WSS4J would use SHA-1 there whatever the MGF child says, and disagree with
     * what this gateway actually computed.
     */
    @Test
    void theLegacyMgf1pKeyTransportUriIsNotUsed() throws Exception {
        assertFalse(rawBody().contains("rsa-oaep-mgf1p"), rawBody());
    }

    @Test
    void theEncryptedKeyHasTheChildOrderWss4jEmits() {
        assertEquals(List.of("xenc:EncryptionMethod", "ds:KeyInfo", "xenc:CipherData", "xenc:ReferenceList"),
                childNames(encryptedKey()));
        assertFalse(encryptedKey().getAttribute("Id").isEmpty(), "an EncryptedKey needs an Id");
    }

    /** The recipient is named by a thumbprint STR/KeyIdentifier, as WSS4J does for encryption. */
    @Test
    void theRecipientKeyInfoIsAThumbprintSecurityTokenReference() {
        Element keyInfo = getFirstChildByName(encryptedKey(), DS_NS, "KeyInfo");
        Element str = getFirstChildByName(keyInfo, WSSE_NS, "SecurityTokenReference");
        assertNotNull(str, "expected a wsse:SecurityTokenReference inside ds:KeyInfo");
        assertEquals(X509_V3_VALUE_TYPE, str.getAttributeNS(WSSE11_NS, "TokenType"));

        Element keyIdentifier = getFirstChildByName(str, WSSE_NS, "KeyIdentifier");
        assertEquals(THUMBPRINT_SHA1_VALUE_TYPE, keyIdentifier.getAttribute("ValueType"));
        assertEquals(BASE64_BINARY_ENCODING_TYPE, keyIdentifier.getAttribute("EncodingType"));
    }

    @Test
    void theEncryptedDataCarriesTheContentTypeAndNoKeyInfo() {
        Element encryptedData = firstByTag(encrypted, XENC_NS, "EncryptedData");

        assertEquals("http://www.w3.org/2001/04/xmlenc#Content", encryptedData.getAttribute("Type"));
        assertEquals(TYPE_CONTENT, encryptedData.getAttribute("Type"));
        assertFalse(encryptedData.getAttribute("Id").isEmpty());
        // No ds:KeyInfo of its own: the EncryptedKey's ReferenceList points forward at it, which is
        // the WSS4J/CXF convention.
        assertEquals(List.of("xenc:EncryptionMethod", "xenc:CipherData"), childNames(encryptedData));
    }

    /**
     * DOM only materializes namespace declarations at serialization time, so a signature listed after
     * an encrypt would canonicalize a subtree that has none. They are therefore written explicitly.
     */
    @Test
    void theXencPrefixIsDeclaredExplicitlyOnTheEmittedElements() throws Exception {
        assertTrue(rawBody().contains("xmlns:xenc=\"" + XENC_NS + "\""), rawBody());
        assertTrue(rawBody().contains("xmlns:xenc11=\"" + XENC11_NS + "\""), rawBody());
    }

    /** The GCM CipherValue is IV || ciphertext || tag, so it can never be shorter than 12 + 16. */
    @Test
    void theCipherValueIsLongEnoughToHoldAnIvAndATag() {
        Element cipherData = getFirstChildByName(
                firstByTag(encrypted, XENC_NS, "EncryptedData"), XENC_NS, "CipherData");
        byte[] raw = java.util.Base64.getDecoder().decode(
                getFirstChildByName(cipherData, XENC_NS, "CipherValue").getTextContent().replaceAll("\\s", ""));

        assertTrue(raw.length > 12 + 16, "expected IV(12) + ciphertext + tag(16), got " + raw.length);
    }
}
