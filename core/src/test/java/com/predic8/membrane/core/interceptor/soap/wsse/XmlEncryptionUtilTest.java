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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Two things, kept apart on purpose.
 * <p>
 * First the namespace-fixup and fragment round trip, deliberately with no cipher anywhere in the
 * picture. Serializing a subtree and parsing it back is the whole of XML Encryption's structural
 * risk: the plaintext leaves a document where its prefixes are declared on ancestors and arrives
 * somewhere it has to stand on its own. Testing it against a cipher as well would only make a
 * failure harder to localize.
 * <p>
 * Then the algorithm dispatch and the key lengths, with no XML in the picture. Every part of the
 * gateway asks for an algorithm by URI, so a URI mapped to the wrong cipher or the wrong key length
 * is silent until a real peer disagrees.
 */
class XmlEncryptionUtilTest {

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Element firstChildElement(Element parent) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                return element;
            }
        }
        throw new AssertionError("no child element");
    }

    /**
     * Serializes the target's content, puts the bytes back through the wrapper parse, and re-attaches
     * them - i.e. exactly what encrypt and decrypt do, minus the encryption.
     */
    private static void roundTripContent(Element target) throws Exception {
        byte[] plaintext = serializeForContentEncryption(target);
        List<Node> restored = parsePlaintextFragment(plaintext, target, target.getOwnerDocument());
        while (target.getFirstChild() != null) {
            target.removeChild(target.getFirstChild());
        }
        restored.forEach(target::appendChild);
    }

    /**
     * The core case: the encrypted content uses a prefix declared on an ancestor that does not travel
     * with it. Without declaration injection the fragment does not even parse.
     */
    @Test
    void contentUsingAnAncestorDeclaredPrefixSurvivesTheRoundTrip() throws Exception {
        Document doc = parse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:biz="http://example.com/business">
                    <soap:Body><biz:order id="7"><biz:item>widget</biz:item></biz:order></soap:Body>
                </soap:Envelope>
                """);
        Element body = firstChildElement(doc.getDocumentElement());

        roundTripContent(body);

        Element order = firstChildElement(body);
        assertEquals("http://example.com/business", order.getNamespaceURI());
        assertEquals("order", order.getLocalName());
        assertEquals("7", order.getAttribute("id"));
        assertEquals("widget", firstChildElement(order).getTextContent());
    }

    /** Unprefixed content has to land back in the default namespace it came from, not in none. */
    @Test
    void contentUsingAnAncestorDefaultNamespaceSurvivesTheRoundTrip() throws Exception {
        Document doc = parse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns="http://example.com/default">
                    <soap:Body><order><item>widget</item></order></soap:Body>
                </soap:Envelope>
                """);
        Element body = firstChildElement(doc.getDocumentElement());

        roundTripContent(body);

        Element order = firstChildElement(body);
        assertEquals("http://example.com/default", order.getNamespaceURI());
        assertEquals("http://example.com/default", firstChildElement(order).getNamespaceURI());
    }

    /** A declaration inside the fragment must win over the one the wrapper re-declares. */
    @Test
    void aPrefixRedeclaredInsideTheFragmentShadowsTheAncestorDeclaration() throws Exception {
        Document doc = parse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:biz="http://example.com/outer">
                    <soap:Body><biz:order xmlns:biz="http://example.com/inner"/></soap:Body>
                </soap:Envelope>
                """);
        Element body = firstChildElement(doc.getDocumentElement());

        roundTripContent(body);

        assertEquals("http://example.com/inner", firstChildElement(body).getNamespaceURI());
    }

    @Test
    void elementEncryptionKeepsTheTargetsOwnNameAndNamespace() throws Exception {
        Document doc = parse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:biz="http://example.com/business">
                    <soap:Body><biz:order><biz:item>widget</biz:item></biz:order></soap:Body>
                </soap:Envelope>
                """);
        Element body = firstChildElement(doc.getDocumentElement());
        Element order = firstChildElement(body);

        byte[] plaintext = serializeForElementEncryption(order);
        List<Node> restored = parsePlaintextFragment(plaintext, body, doc);

        assertEquals(1, restored.size());
        Element parsed = (Element) restored.getFirst();
        assertEquals("http://example.com/business", parsed.getNamespaceURI());
        assertEquals("order", parsed.getLocalName());
        assertEquals("widget", firstChildElement(parsed).getTextContent());
    }

    /**
     * A literal carriage return in character data is normalized to a line feed by every conforming
     * parser, so it has to be escaped numerically or the payload changes silently in transit.
     */
    @Test
    void carriageReturnInTextContentIsPreserved() throws Exception {
        Document doc = parse("""
                <root xmlns="http://example.com/d"><holder/></root>
                """);
        Element holder = firstChildElement(doc.getDocumentElement());
        holder.appendChild(doc.createTextNode("line one\r\nline two"));

        roundTripContent(holder);

        assertEquals("line one\r\nline two", holder.getTextContent());
    }

    @Test
    void mixedTextAndElementContentSurvivesTheRoundTrip() throws Exception {
        Document doc = parse("""
                <root xmlns:biz="http://example.com/business"><holder>before<biz:x/>after</holder></root>
                """);
        Element holder = firstChildElement(doc.getDocumentElement());

        roundTripContent(holder);

        assertEquals("beforeafter", holder.getTextContent());
        assertEquals("http://example.com/business", firstChildElement(holder).getNamespaceURI());
    }

    /** Markup in character data must not become markup again on the way back. */
    @Test
    void markupCharactersInTextContentAreEscaped() throws Exception {
        Document doc = parse("<root><holder/></root>");
        Element holder = firstChildElement(doc.getDocumentElement());
        holder.appendChild(doc.createTextNode("a < b & c > d"));

        roundTripContent(holder);

        assertEquals("a < b & c > d", holder.getTextContent());
        assertEquals(0, holder.getElementsByTagName("*").getLength());
    }

    /**
     * Indentation would survive into the decrypted content and change the bytes a signature made
     * before encryption had digested.
     */
    @Test
    void serializationIntroducesNoIndentation() throws Exception {
        Document doc = parse("""
                <root xmlns="http://example.com/d"><holder><a><b>x</b></a></holder></root>
                """);
        Element holder = firstChildElement(doc.getDocumentElement());

        String plaintext = new String(serializeForContentEncryption(holder), UTF_8);

        assertFalse(plaintext.contains("\n"), "serialized fragment must not be pretty-printed: " + plaintext);
        assertTrue(plaintext.startsWith("<a"), plaintext);
    }

    /** Content encryption of an empty element is legal and must produce an empty fragment. */
    @Test
    void emptyContentSerializesToNothingAndRestoresToNothing() throws Exception {
        Document doc = parse("<root><holder/></root>");
        Element holder = firstChildElement(doc.getDocumentElement());

        assertEquals(0, serializeForContentEncryption(holder).length);

        roundTripContent(holder);

        assertFalse(holder.hasChildNodes());
    }

    // ---- algorithms ----------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            AES128_GCM + ",16",
            AES256_GCM + ",32",
            AES128_CBC + ",16",
            AES192_CBC + ",24",
            AES256_CBC + ",32",
    })
    void everySupportedAlgorithmHasItsKeyLength(String algorithm, int expectedLength) {
        assertEquals(expectedLength, cekLengthFor(algorithm));
    }

    /**
     * An algorithm nobody mapped must not silently get a plausible-looking 16, which is what the
     * earlier expression returned for everything that was not AES-256-GCM.
     */
    @Test
    void anUnmappedAlgorithmHasNoKeyLength() {
        assertThrows(IllegalStateException.class, () -> cekLengthFor(XENC_NS + "tripledes-cbc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {AES128_GCM, AES256_GCM, AES128_CBC, AES192_CBC, AES256_CBC})
    void everySupportedAlgorithmRoundTripsItsPlaintext(String algorithm) throws Exception {
        SecretKey key = generateContentEncryptionKey(algorithm);
        byte[] plaintext = "<a>hello</a>".getBytes(UTF_8);

        byte[] ciphertext = encryptData(algorithm, key, plaintext);

        assertArrayEquals(plaintext, decryptData(algorithm, key, ciphertext));
    }

    @ParameterizedTest
    @ValueSource(strings = {AES128_CBC, AES192_CBC, AES256_CBC})
    void cbcKeysHaveTheLengthTheirUriPromises(String algorithm) throws Exception {
        assertEquals(cekLengthFor(algorithm), generateContentEncryptionKey(algorithm).getEncoded().length);
    }

    /** {@code IV || ciphertext}, with the ciphertext block-aligned - the layout a peer expects. */
    @Test
    void cbcCiphertextIsAnIvFollowedByWholeBlocks() throws Exception {
        byte[] ciphertext = encryptCbc(generateContentEncryptionKey(AES256_CBC), "x".getBytes(UTF_8));

        assertEquals(0, ciphertext.length % 16);
        assertTrue(ciphertext.length >= 32, "an IV plus at least one block, was " + ciphertext.length);
    }

    /**
     * Rejected before any {@code Cipher} sees it. Reaching the cipher with a truncated input would
     * produce a differently-shaped failure than a wrong key does, which is a distinction an attacker
     * can measure.
     */
    @Test
    void cbcCiphertextShorterThanAnIvAndABlockIsRejected() throws Exception {
        SecretKey key = generateContentEncryptionKey(AES256_CBC);

        assertThrows(GeneralSecurityException.class, () -> decryptCbc(key, new byte[31]));
    }

    /** The two families have to actually differ; a dispatch that always chose GCM would pass a
     * round-trip test but produce something no CBC peer can read. */
    @Test
    void theDispatchSelectsDifferentCiphersForCbcAndGcm() throws Exception {
        SecretKey key = generateContentEncryptionKey(AES256_CBC);
        byte[] plaintext = "<a>hello</a>".getBytes(UTF_8);

        byte[] cbc = encryptData(AES256_CBC, key, plaintext);
        byte[] gcm = encryptData(AES256_GCM, key, plaintext);

        // CBC prefixes a 16-byte IV and pads to a block; GCM prefixes 12 and appends a 16-byte tag.
        assertEquals(0, cbc.length % 16);
        assertEquals(12 + plaintext.length + 16, gcm.length);
    }

    @Test
    void theKeyTransportDispatchSelectsPkcs1ForRsa15() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        byte[] contentEncryptionKey = generateContentEncryptionKey(AES256_CBC).getEncoded();

        byte[] wrapped = keyTransportCipher(RSA_1_5, Cipher.ENCRYPT_MODE, keyPair.getPublic())
                .doFinal(contentEncryptionKey);

        assertArrayEquals(contentEncryptionKey,
                keyTransportCipher(RSA_1_5, Cipher.DECRYPT_MODE, keyPair.getPrivate()).doFinal(wrapped));
        // And is not interchangeable with OAEP, which is the mistake a single shared factory invites.
        assertThrows(GeneralSecurityException.class,
                () -> keyTransportCipher(RSA_OAEP, Cipher.DECRYPT_MODE, keyPair.getPrivate()).doFinal(wrapped));
    }
}
