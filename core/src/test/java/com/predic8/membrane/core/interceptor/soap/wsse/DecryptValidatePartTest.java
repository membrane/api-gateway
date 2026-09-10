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

import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.getFirstChildByName;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class DecryptValidatePartTest extends AbstractWsSecurityTest {

    private static final String PLAINTEXT_BODY = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    /**
     * Encrypts the current exchange body for {@code recipientAlias} and republishes it, so the
     * decrypt side starts from a real serialized message rather than a shared in-memory DOM.
     */
    private void encryptFor(String recipientAlias, EncryptionReference... references) throws Exception {
        EncryptionReference[] refs = references.length == 0
                ? new EncryptionReference[]{encryptedBodyReference()}
                : references;
        assertEquals(Outcome.CONTINUE,
                encrypter(recipientAlias.equals(ALIAS_1) ? TRUSTSTORE : TRUSTSTORE_KEY2,
                        encrypt(recipientAlias, refs)).handleRequest(exchange));
    }

    @Test
    void roundTripsAnEncryptedBody() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        assertEquals(Outcome.CONTINUE,
                decrypter(ALIAS_1, decrypt(encryptedBodyReference())).handleRequest(exchange));

        Document doc = parseBody();
        assertEquals("bar", doc.getElementsByTagName("foo").item(0).getTextContent());
        assertEquals(0, doc.getElementsByTagNameNS(XENC_NS, "EncryptedData").getLength());
    }

    @ParameterizedTest
    @ValueSource(strings = {AES128_GCM, AES256_GCM})
    void roundTripsBothAesKeySizes(String algorithm) throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        encrypt.setDataEncryptionAlgorithm(algorithm);
        encrypter(TRUSTSTORE, encrypt).handleRequest(exchange);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, decrypt()).handleRequest(exchange));

        assertEquals("bar", parseBody().getElementsByTagName("foo").item(0).getTextContent());
    }

    /** The spent key material must not travel onward once the ciphertext it unlocked is gone. */
    @Test
    void theSpentEncryptedKeyIsRemoved() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        decrypter(ALIAS_1, decrypt()).handleRequest(exchange);

        Document doc = parseBody();
        assertEquals(0, doc.getElementsByTagNameNS(XENC_NS, "EncryptedKey").getLength());
        assertEquals(0, doc.getElementsByTagNameNS(WsSecurityXmlUtil.WSSE_NS, "Security").getLength());
    }

    @Test
    void elementEncryptionRoundTripsAnAncestorNamespacedPayload() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:biz="http://example.com/business">
                    <soap:Body><biz:order><biz:item>widget</biz:item></biz:order></soap:Body>
                </soap:Envelope>
                """);
        EncryptionReference ref = new EncryptionReference();
        ref.setXpath("//*[local-name()='order']");
        ref.setType(EncryptionReference.Type.ELEMENT);
        encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)).handleRequest(exchange);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, decrypt()).handleRequest(exchange));

        Element order = firstByTag(parseBody(), "http://example.com/business", "order");
        assertEquals("widget", order.getTextContent().trim());
    }

    /**
     * Content decryption has to put the plaintext back where the ciphertext stood. Appended to the
     * parent instead, it would move behind any sibling that parent carries - and a validate/signature
     * listed after this part would then canonicalize a different node order than the sender signed.
     */
    @Test
    void contentDecryptionRestoresThePlaintextWhereTheCiphertextStood() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        // A sibling behind the ciphertext, so appending and inserting are distinguishable at all.
        Document sent = parseBody();
        Element body = firstByTag(sent, SOAP_NS, "Body");
        body.appendChild(sent.createElement("tail"));
        setBody(sent);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, decrypt()).handleRequest(exchange));

        assertEquals(List.of("foo", "tail"),
                WsSecurityXmlUtil.childElementsOf(firstByTag(parseBody(), SOAP_NS, "Body")).stream()
                        .map(Element::getLocalName).toList());
    }

    // ---- cryptographic failure -----------------------------------------------------------------

    @Test
    void aMessageEncryptedForAnotherRecipientFails() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_2);

        assertFault(decrypter(ALIAS_1, decrypt()), FAILED_CHECK);
    }

    /** GCM's authentication tag is what turns a tampered ciphertext into a refusal. */
    @Test
    void aTamperedCiphertextFailsTheAuthenticationTag() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        // The EncryptedData's own CipherValue, not the EncryptedKey's: the point is that the payload
        // was altered while the key material stayed intact and openable.
        Element dataCipherValue = (Element) firstByTag(doc, XENC_NS, "EncryptedData")
                .getElementsByTagNameNS(XENC_NS, "CipherValue").item(0);
        String tampered = dataCipherValue.getTextContent().trim();
        dataCipherValue.setTextContent((tampered.charAt(0) == 'A' ? 'B' : 'A') + tampered.substring(1));
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), FAILED_CHECK);
    }

    // ---- algorithm allowlist -------------------------------------------------------------------

    /** The settled CBC rejection has to be enforced on the way in, not only in configuration. */
    @Test
    void anInboundCbcDataAlgorithmIsRefused() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element encryptedData = firstByTag(doc, XENC_NS, "EncryptedData");
        getFirstChildByName(encryptedData, XENC_NS, "EncryptionMethod")
                .setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#aes256-cbc");
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), UNSUPPORTED_ALGORITHM);
    }

    @Test
    void anInboundRsa15KeyTransportAlgorithmIsRefused() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element encryptedKey = firstByTag(doc, XENC_NS, "EncryptedKey");
        getFirstChildByName(encryptedKey, XENC_NS, "EncryptionMethod")
                .setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#rsa-1_5");
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), UNSUPPORTED_ALGORITHM);
    }

    /**
     * The modern OAEP URI exists so the mask generation function is explicit; sending it with an
     * SHA-1 MGF is a downgrade wearing a modern name.
     */
    @Test
    void anSha1MaskGenerationFunctionIsRefused() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element method = getFirstChildByName(firstByTag(doc, XENC_NS, "EncryptedKey"), XENC_NS, "EncryptionMethod");
        getFirstChildByName(method, XENC11_NS, "MGF")
                .setAttribute("Algorithm", "http://www.w3.org/2009/xmlenc11#mgf1sha1");
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), UNSUPPORTED_ALGORITHM);
    }

    @Test
    void anAbsentMaskGenerationFunctionIsRefused() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element method = getFirstChildByName(firstByTag(doc, XENC_NS, "EncryptedKey"), XENC_NS, "EncryptionMethod");
        method.removeChild(getFirstChildByName(method, XENC11_NS, "MGF"));
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), UNSUPPORTED_ALGORITHM);
    }

    // ---- structural rejection ------------------------------------------------------------------

    /** Configuring decrypt states that the message has to be confidential. */
    @Test
    void aPlaintextMessageWithNoEncryptedKeyIsRejected() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);

        assertFault(decrypter(ALIAS_1, decrypt()), INVALID_SECURITY);
    }

    @Test
    void twoEncryptedKeysAreRejectedAsAmbiguous() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element encryptedKey = firstByTag(doc, XENC_NS, "EncryptedKey");
        Element duplicate = (Element) encryptedKey.cloneNode(true);
        duplicate.setAttribute("Id", "EK-second");
        encryptedKey.getParentNode().appendChild(duplicate);
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), INVALID_SECURITY);
    }

    /** An ambiguous Id would let an attacker aim the decryption at an element of their choosing. */
    @Test
    void aDuplicatedEncryptedDataIdIsRejectedAsAmbiguous() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element encryptedData = firstByTag(doc, XENC_NS, "EncryptedData");
        Element decoy = (Element) encryptedData.cloneNode(true);
        firstByTag(doc, SOAP_NS, "Body").appendChild(decoy);
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), INVALID_SECURITY);
    }

    /**
     * Ciphertext nobody referenced would otherwise reach the backend uninspected - the
     * confidentiality counterpart of the signature-wrapping defence.
     */
    @Test
    void anUnreferencedEncryptedDataLeftInTheMessageIsRejected() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element stray = (Element) firstByTag(doc, XENC_NS, "EncryptedData").cloneNode(true);
        stray.setAttribute("Id", "ED-unreferenced");
        firstByTag(doc, SOAP_NS, "Body").appendChild(stray);
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), INVALID_SECURITY);
    }

    // ---- requiredReferences --------------------------------------------------------------------

    /**
     * The check that makes confidentiality enforceable: encrypting one child and leaving its sibling
     * readable must not satisfy a required BODY reference, even though the body does then contain an
     * xenc:EncryptedData.
     */
    @Test
    void aRequiredBodyReferenceFailsWhenOnlyPartOfTheBodyIsEncrypted() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        <foo>bar</foo>
                        <readable>in the clear</readable>
                    </soap:Body>
                </soap:Envelope>
                """);
        EncryptionReference ref = new EncryptionReference();
        ref.setXpath("//*[local-name()='foo']");
        ref.setType(EncryptionReference.Type.ELEMENT);
        encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)).handleRequest(exchange);

        assertFault(decrypter(ALIAS_1, decrypt(encryptedBodyReference())), FAILED_CHECK);
    }

    /**
     * A peer that sends a security header but encrypted nothing gets the same structural fault as one
     * that sent no key material for any other reason - not a policy failure - because the structural
     * checks run first. Pinned because it is otherwise an accident of ordering: the fault a user sees
     * for "you didn't encrypt" must not depend on whether they sent an empty header or none at all.
     */
    @Test
    void aPlaintextMessageWithAnEmptySecurityHeaderIsRejectedStructurally() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s"/>
                    </soap:Header>
                    <soap:Body>
                        <foo>bar</foo>
                    </soap:Body>
                </soap:Envelope>
                """.formatted(WsSecurityXmlUtil.WSSE_NS));

        assertFault(decrypter(ALIAS_1, decrypt(encryptedBodyReference())), INVALID_SECURITY);
    }

    /** The whole body encrypted as content is what the required reference is asking for. */
    @Test
    void aRequiredBodyReferenceIsSatisfiedByAFullyEncryptedBody() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        assertEquals(Outcome.CONTINUE,
                decrypter(ALIAS_1, decrypt(encryptedBodyReference())).handleRequest(exchange));
    }

    /**
     * Pins the documented limitation: with no requiredReferences, decrypt asserts only that what
     * arrived encrypted was decryptable.
     */
    @Test
    void withoutRequiredReferencesAPartiallyEncryptedMessageIsAccepted() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        EncryptionReference ref = new EncryptionReference();
        ref.setXpath("//*[local-name()='foo']");
        ref.setType(EncryptionReference.Type.ELEMENT);
        encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)).handleRequest(exchange);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, decrypt()).handleRequest(exchange));
    }

    /**
     * The element-encrypted counterpart of the BODY case, and the reason the two reference types are
     * checked at different moments: element encryption replaces the wsse:UsernameToken outright, so
     * while the message is still encrypted there is no token for the reference to resolve to. Checked
     * up front, this requirement would reject exactly the message that satisfies it.
     */
    @Test
    void aRequiredElementReferenceIsSatisfiedByAnElementEncryptedToken() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        sendUsernameTokenEncryptedAs(EncryptionReference.Type.ELEMENT);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1,
                decrypt(encryptionReference(EncryptionReference.By.USERNAME_TOKEN))).handleRequest(exchange));

        // The token itself does not survive - it is an unvalidated claim, dropped with the rest of
        // the consumed header. What is asserted here is that the requirement was met, not the token.
        assertEquals("bar", parseBody().getElementsByTagName("foo").item(0).getTextContent());
    }

    /**
     * The check has to be about how the element arrived, not merely about it being there once
     * everything has been decrypted - otherwise a peer could send the token in the clear next to any
     * unrelated ciphertext and pass.
     */
    @Test
    void aRequiredElementReferenceFailsWhenTheTokenArrivedInTheClear() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        sendUsernameTokenEncryptedAs(null);

        assertFault(decrypter(ALIAS_1,
                decrypt(encryptionReference(EncryptionReference.By.USERNAME_TOKEN))), FAILED_CHECK);
    }

    /**
     * A wsse:UsernameToken plus an encrypt of the body, and - when {@code tokenType} is given - of the
     * token itself. Crosses the wire, so the receiver starts from real bytes.
     */
    private void sendUsernameTokenEncryptedAs(EncryptionReference.Type tokenType) throws Exception {
        UsernameTokenSecurePart token = new UsernameTokenSecurePart();
        token.setUsername("alice");
        token.setPassword("secret");

        List<EncryptionReference> references = new java.util.ArrayList<>(List.of(encryptedBodyReference()));
        if (tokenType != null) {
            references.add(encryptionReference(EncryptionReference.By.USERNAME_TOKEN, tokenType));
        }
        EncryptSecurePart encrypt = encrypt(ALIAS_1, references.toArray(new EncryptionReference[0]));

        assertEquals(Outcome.CONTINUE,
                encrypter(TRUSTSTORE, token, encrypt).handleRequest(exchange));
        crossTheWire();
    }

    /**
     * Naming one xenc:EncryptedData twice used to leave the second pass with a node the first had
     * already detached, which surfaced as an internal error rather than a fault.
     */
    @Test
    void anEncryptedDataNamedTwiceIsRejected() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element referenceList = firstByTag(doc, XENC_NS, "ReferenceList");
        Element dataReference = getFirstChildByName(referenceList, XENC_NS, "DataReference");
        referenceList.appendChild(dataReference.cloneNode(true));
        setBody(doc);

        assertFault(decrypter(ALIAS_1, decrypt()), INVALID_SECURITY);
    }

    // ---- configuration -------------------------------------------------------------------------

    @Test
    void decryptWithoutAKeystoreIsRejected() {
        WsSecurityInterceptor wsSecurity = validating(decrypt());
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> wsSecurity.init(router));
        assertTrue(e.getMessage().contains("keystore"), e.getMessage());
    }

    @Test
    void decryptTakesNoAlgorithmAttributes() {
        // The fixed allowlist is the point: there is no setter to weaken it with.
        assertTrue(java.util.Arrays.stream(DecryptValidatePart.class.getMethods())
                .noneMatch(m -> m.getName().equals("setDataEncryptionAlgorithm")
                                || m.getName().equals("setKeyTransportAlgorithm")));
    }

    @Test
    void requiredReferencesAreValidatedAtStartup() {
        EncryptionReference ref = encryptionReference(EncryptionReference.By.BODY, EncryptionReference.Type.ELEMENT);
        DecryptValidatePart decrypt = new DecryptValidatePart();
        decrypt.setRequiredReferences(List.of(ref));

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> decrypter(ALIAS_1, decrypt));
        assertTrue(e.getMessage().contains("BODY"), e.getMessage());
    }
}
