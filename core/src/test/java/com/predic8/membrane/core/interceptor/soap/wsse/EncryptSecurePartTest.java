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

import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.SOAPUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class EncryptSecurePartTest extends AbstractWsSecurityTest {

    private static final String SOAP_BODY_WITH_TOKEN = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    /**
     * The decrypted plaintext must contain the original element, but not necessarily verbatim: the
     * serialized fragment carries the namespace declarations that were in scope where it sat, so
     * {@code <foo>} legitimately comes back as {@code <foo xmlns:soap="...">}. Matching the start tag
     * and the content separately is what makes the assertion robust to that without weakening it.
     */
    private static void assertDecryptsToFooBar(String plaintext) {
        assertTrue(plaintext.contains("<foo"), plaintext);
        assertTrue(plaintext.contains(">bar</foo>"), plaintext);
    }

    /** Decrypts an {@code xenc:EncryptedData} independently of the part that produced it. */
    private String decryptIndependently(Document doc, String alias) throws Exception {
        Element encryptedKey = firstByTag(doc, XENC_NS, "EncryptedKey");
        Element encryptedData = firstByTag(doc, XENC_NS, "EncryptedData");

        byte[] wrapped = Base64.getDecoder().decode(cipherValue(encryptedKey));
        byte[] cek = XmlEncryptionUtil.rsaOaepCipher(Cipher.DECRYPT_MODE, privateKey(alias)).doFinal(wrapped);

        byte[] plaintext = decryptGcm(new SecretKeySpec(cek, "AES"),
                Base64.getDecoder().decode(cipherValue(encryptedData)));
        return new String(plaintext, UTF_8);
    }

    private static String cipherValue(Element encryptedElement) {
        Element cipherData = getFirstChildByName(encryptedElement, XENC_NS, "CipherData");
        return getFirstChildByName(cipherData, XENC_NS, "CipherValue").getTextContent().replaceAll("\\s", "");
    }

    @Test
    void encryptsTheBodyContentAndLeavesTheBodyElementInPlace() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);

        assertEquals(Outcome.CONTINUE,
                encrypter(TRUSTSTORE, encrypt(ALIAS_1, encryptedBodyReference())).handleRequest(exchange));

        Document doc = parseBody();
        Element body = firstByTag(doc, SOAP_NS, "Body");
        assertEquals(1, body.getElementsByTagNameNS(XENC_NS, "EncryptedData").getLength());
        assertEquals(0, doc.getElementsByTagName("foo").getLength(), "the plaintext must be gone");
        assertFalse(rawBody().contains("bar"), "the plaintext must not survive anywhere in the message");
    }

    /** Content encryption exists so that the envelope stays a SOAP envelope. */
    @Test
    void contentEncryptionKeepsTheEnvelopeValidSoap() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);

        encrypter(TRUSTSTORE, encrypt(ALIAS_1, encryptedBodyReference())).handleRequest(exchange);

        assertTrue(SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), exchange.getRequest()).isSOAP());
    }

    @Test
    void theEncryptedBodyRoundTripsBackToItsPlaintext() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);

        encrypter(TRUSTSTORE, encrypt(ALIAS_1, encryptedBodyReference())).handleRequest(exchange);

        assertDecryptsToFooBar(decryptIndependently(parseBody(), ALIAS_1));
    }

    @ParameterizedTest
    @ValueSource(strings = {AES128_GCM, AES256_GCM})
    void bothAesKeySizesProduceADecryptableMessage(String algorithm) throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        encrypt.setDataEncryptionAlgorithm(algorithm);

        encrypter(TRUSTSTORE, encrypt).handleRequest(exchange);

        assertDecryptsToFooBar(decryptIndependently(parseBody(), ALIAS_1));
    }

    /**
     * The emitted URI has to be the configured one byte for byte, or a receiver decrypts with a
     * different algorithm than the one actually used.
     */
    @Test
    void theEmittedEncryptionMethodMatchesTheConfiguredAlgorithm() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        encrypt.setDataEncryptionAlgorithm(AES128_GCM);

        encrypter(TRUSTSTORE, encrypt).handleRequest(exchange);

        Element encryptedData = firstByTag(parseBody(), XENC_NS, "EncryptedData");
        assertEquals(AES128_GCM,
                getFirstChildByName(encryptedData, XENC_NS, "EncryptionMethod").getAttribute("Algorithm"));
    }

    @Test
    void theEncryptedKeyReferencesEveryEncryptedDataItProtects() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);

        encrypter(TRUSTSTORE, encrypt(ALIAS_1, encryptedBodyReference())).handleRequest(exchange);

        Document doc = parseBody();
        Element referenceList = firstByTag(doc, XENC_NS, "ReferenceList");
        Element dataReference = getFirstChildByName(referenceList, XENC_NS, "DataReference");
        assertEquals("#" + firstByTag(doc, XENC_NS, "EncryptedData").getAttribute("Id"),
                dataReference.getAttribute("URI"));
    }

    /** A thumbprint KeyIdentifier, because the recipient already holds the certificate. */
    @Test
    void keyInfoDefaultsToAThumbprintKeyIdentifier() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);

        encrypter(TRUSTSTORE, encrypt(ALIAS_1, encryptedBodyReference())).handleRequest(exchange);

        Element keyIdentifier = firstByTag(parseBody(), WSSE_NS, "KeyIdentifier");
        assertEquals(THUMBPRINT_SHA1_VALUE_TYPE, keyIdentifier.getAttribute("ValueType"));
    }

    /**
     * The same bare {@code keyIdentifier} element means X509_V3 under a signature and
     * THUMBPRINT_SHA1 under an encrypt, which is what the per-parent default exists for.
     */
    @Test
    void aBareKeyIdentifierMeansThumbprintUnderEncrypt() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        encrypt.setKeyIdentifier(new KeyIdentifierKeyInfo());

        encrypter(TRUSTSTORE, encrypt).handleRequest(exchange);

        assertEquals(THUMBPRINT_SHA1_VALUE_TYPE,
                firstByTag(parseBody(), WSSE_NS, "KeyIdentifier").getAttribute("ValueType"));
    }

    @Test
    void anExplicitX509V3KeyIdentifierEmbedsTheCertificate() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        KeyIdentifierKeyInfo keyIdentifier = new KeyIdentifierKeyInfo();
        keyIdentifier.setValueType(KeyIdentifierKeyInfo.ValueType.X509_V3);
        encrypt.setKeyIdentifier(keyIdentifier);

        encrypter(TRUSTSTORE, encrypt).handleRequest(exchange);

        Element element = firstByTag(parseBody(), WSSE_NS, "KeyIdentifier");
        assertEquals(X509_V3_VALUE_TYPE, element.getAttribute("ValueType"));
        assertEquals(Base64.getEncoder().encodeToString(certificate(ALIAS_1).getEncoded()),
                element.getTextContent().replaceAll("\\s", ""));
    }

    /** An XPath reference encrypts every element it matches, each under the same key. */
    @Test
    void anXpathReferenceEncryptsEachMatchedElement() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        <order><secret>a</secret><secret>b</secret></order>
                    </soap:Body>
                </soap:Envelope>
                """);
        EncryptionReference ref = new EncryptionReference();
        ref.setXpath("//*[local-name()='secret']");

        assertEquals(Outcome.CONTINUE, encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)).handleRequest(exchange));

        Document doc = parseBody();
        assertEquals(2, doc.getElementsByTagNameNS(XENC_NS, "EncryptedData").getLength());
        assertEquals(2, firstByTag(doc, XENC_NS, "ReferenceList")
                .getElementsByTagNameNS(XENC_NS, "DataReference").getLength());
        assertEquals(0, doc.getElementsByTagName("secret").getLength());
    }

    @Test
    void anXpathReferenceMatchingNothingIsAFault() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);
        EncryptionReference ref = new EncryptionReference();
        ref.setXpath("//*[local-name()='absent']");

        assertFault(encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)), WsSecurityFaultCode.INVALID_SECURITY);
    }

    // ---- recipient alias resolution ------------------------------------------------------------

    /**
     * A truststore normally holds {@code trustedCertEntry} entries, but a p12 holding a
     * {@code PrivateKeyEntry} is equally valid as one - and that is what the tutorial uses. Both have
     * to resolve, which rules out {@code KeyStoreUtil.aliasOrThrow}: it gates on {@code isKeyEntry}
     * and so finds neither reliably.
     */
    @Test
    void recipientAliasResolvesAgainstAStoreHoldingPrivateKeyEntries() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_TOKEN);
        TrustStore asTrustStore = new TrustStore();
        asTrustStore.setLocation(KEYSTORE);
        asTrustStore.setPassword(KEYSTORE_PASSWORD);

        WsSecurityInterceptor wsSecurity = securing(encrypt(ALIAS_2, encryptedBodyReference()));
        wsSecurity.setTrustStore(asTrustStore);
        wsSecurity.init(router);

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
        assertDecryptsToFooBar(decryptIndependently(parseBody(), ALIAS_2));
    }

    @Test
    void anUnknownRecipientAliasNamesTheAvailableOnes() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> encrypter(TRUSTSTORE, encrypt("nosuchalias", encryptedBodyReference())));
        assertTrue(e.getMessage().contains("nosuchalias"), e.getMessage());
        assertTrue(e.getMessage().contains(ALIAS_1), "the message must list what is available: " + e.getMessage());
    }

    // ---- configuration rejection ---------------------------------------------------------------

    @Test
    void encryptWithoutATruststoreIsRejected() {
        WsSecurityInterceptor wsSecurity = securing(encrypt(ALIAS_1, encryptedBodyReference()));
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> wsSecurity.init(router));
        assertTrue(e.getMessage().contains("truststore"), e.getMessage());
    }

    @Test
    void encryptWithoutARecipientAliasIsRejected() {
        EncryptSecurePart encrypt = new EncryptSecurePart();
        encrypt.setReferences(java.util.List.of(encryptedBodyReference()));

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> encrypter(TRUSTSTORE, encrypt));
        assertTrue(e.getMessage().contains("recipientAlias"), e.getMessage());
    }

    @Test
    void encryptWithoutReferencesIsRejected() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> encrypter(TRUSTSTORE, encrypt(ALIAS_1)));
        assertTrue(e.getMessage().contains("<reference>"), e.getMessage());
    }

    /**
     * {@code keyIdentifier} is the only key-info mode here. An {@code x509Data} would produce exactly
     * the same {@code ds:KeyInfo} as {@code valueType: X509_V3}, so offering both would be two
     * spellings of one message.
     */
    @Test
    void encryptTakesNoX509DataChild() {
        assertTrue(java.util.Arrays.stream(EncryptSecurePart.class.getMethods())
                .noneMatch(m -> m.getName().equals("setX509Data")));
    }

    @Test
    void twoReferencesSharingAnIdAreRejected() {
        EncryptionReference first = new EncryptionReference();
        first.setXpath("//*[local-name()='a']");
        first.setId("ED-1");
        EncryptionReference second = new EncryptionReference();
        second.setXpath("//*[local-name()='b']");
        second.setId("ED-1");

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> encrypter(TRUSTSTORE, encrypt(ALIAS_1, first, second)));
        assertTrue(e.getMessage().contains("ED-1"), e.getMessage());
    }

    /** An id becomes an XML {@code ID} and a {@code "#..."} reference, so not every string will do. */
    @Test
    void anIdThatIsNotAnXmlNameIsRejected() {
        EncryptionReference ref = encryptedBodyReference();
        ref.setId("1 not a name");

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)));
        assertTrue(e.getMessage().contains("1 not a name"), e.getMessage());
    }

    /** The CBC modes are the Jager-Somorovsky attack surface and must not be configurable. */
    @Test
    void aCbcDataEncryptionAlgorithmIsRejected() {
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        encrypt.setDataEncryptionAlgorithm("http://www.w3.org/2001/04/xmlenc#aes256-cbc");

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> encrypter(TRUSTSTORE, encrypt));
        assertTrue(e.getMessage().contains("dataEncryptionAlgorithm"), e.getMessage());
    }

    @Test
    void anRsa15KeyTransportAlgorithmIsRejected() {
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        encrypt.setKeyTransportAlgorithm("http://www.w3.org/2001/04/xmlenc#rsa-1_5");

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> encrypter(TRUSTSTORE, encrypt));
        assertTrue(e.getMessage().contains("keyTransportAlgorithm"), e.getMessage());
    }

    /** Replacing soap:Body itself would leave an envelope that is no longer SOAP. */
    @Test
    void elementEncryptionOfTheBodyIsRejected() {
        EncryptionReference ref = encryptionReference(EncryptionReference.By.BODY, EncryptionReference.Type.ELEMENT);

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)));
        assertTrue(e.getMessage().contains("type"), e.getMessage());
        assertTrue(e.getMessage().contains("BODY"), e.getMessage());
    }

    @Test
    void byTogetherWithXpathIsRejected() {
        EncryptionReference ref = new EncryptionReference();
        ref.setBy(EncryptionReference.By.BODY);
        ref.setXpath("//foo");

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> encrypter(TRUSTSTORE, encrypt(ALIAS_1, ref)));
        assertTrue(e.getMessage().contains("xpath"), e.getMessage());
    }
}
