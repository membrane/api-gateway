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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

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

    /**
     * The default rejection has to be enforced on the way in, not only in configuration - and it is
     * the default even though {@code secure/encrypt} can be configured to emit this algorithm.
     */
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

    // ---- allowedLegacyAlgorithms -----------------------------------------------------------------

    /** {@code decrypt} with the legacy opt-in switched on. */
    private static DecryptValidatePart legacyDecrypt(EncryptionReference... requiredReferences) {
        DecryptValidatePart decrypt = decrypt(requiredReferences);
        decrypt.setAllowedLegacyAlgorithms(List.of(LegacyEncryptionAlgorithm.values()));
        return decrypt;
    }

    private void encryptForWith(String dataAlgorithm, String keyTransportAlgorithm) throws Exception {
        EncryptSecurePart encrypt = encrypt(ALIAS_1, encryptedBodyReference());
        encrypt.setDataEncryptionAlgorithm(dataAlgorithm);
        encrypt.setKeyTransportAlgorithm(keyTransportAlgorithm);
        assertEquals(Outcome.CONTINUE, encrypter(TRUSTSTORE, encrypt).handleRequest(exchange));
    }

    private void assertBodyIsPlaintextAgain() throws Exception {
        assertEquals("bar", parseBody().getElementsByTagName("foo").item(0).getTextContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {AES128_CBC, AES192_CBC, AES256_CBC})
    void everyCbcKeySizeRoundTripsWhenLegacyAlgorithmsAreAllowed(String algorithm) throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(algorithm, RSA_OAEP);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, legacyDecrypt()).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(LegacyEncryptionAlgorithm.class)
    void individualExceptionAcceptsOnlyThatLegacyAlgorithm(LegacyEncryptionAlgorithm allowed) throws Exception {
        for (LegacyEncryptionAlgorithm incoming : LegacyEncryptionAlgorithm.values()) {
            exchangeWithBody(PLAINTEXT_BODY);
            boolean keyTransport = incoming == LegacyEncryptionAlgorithm.RSA_1_5;
            encryptForWith(keyTransport ? AES256_GCM : incoming.getUri(),
                    keyTransport ? incoming.getUri() : RSA_OAEP);
            DecryptValidatePart decrypt = decrypt();
            decrypt.setAllowedLegacyAlgorithms(List.of(allowed));
            if (incoming == allowed) {
                assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, decrypt).handleRequest(exchange));
                assertBodyIsPlaintextAgain();
            } else {
                assertFault(decrypter(ALIAS_1, decrypt), UNSUPPORTED_ALGORITHM);
            }
        }
    }

    @Test
    void legacyExceptionPreservesModernDefaults() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_GCM, RSA_OAEP);
        DecryptValidatePart decrypt = decrypt();
        decrypt.setAllowedLegacyAlgorithms(List.of(LegacyEncryptionAlgorithm.AES128_CBC));
        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, decrypt).handleRequest(exchange));
        assertBodyIsPlaintextAgain();
    }

    @Test
    void rsa15KeyTransportRoundTripsWhenLegacyAlgorithmsAreAllowed() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_GCM, RSA_1_5);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, legacyDecrypt()).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    /** The combination a legacy .NET/WCF or older WSS4J peer actually sends. */
    @Test
    void cbcTogetherWithRsa15RoundTripsWhenLegacyAlgorithmsAreAllowed() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_1_5);

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, legacyDecrypt()).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    /** A requiredReferences check has to keep working over legacy ciphertext, not just GCM. */
    @Test
    void aRequiredReferenceIsStillEnforcedOverCbcCiphertext() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_1_5);

        assertEquals(Outcome.CONTINUE,
                decrypter(ALIAS_1, legacyDecrypt(encryptedBodyReference())).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    /** The switch names two algorithm families; it is not a blanket "accept whatever arrives". */
    @Test
    void anUnknownAlgorithmIsRefusedEvenWhenLegacyAlgorithmsAreAllowed() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        getFirstChildByName(firstByTag(doc, XENC_NS, "EncryptedData"), XENC_NS, "EncryptionMethod")
                .setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#tripledes-cbc");
        setBody(doc);

        assertFault(decrypter(ALIAS_1, legacyDecrypt()), UNSUPPORTED_ALGORITHM);
    }

    /**
     * The one thing the switch must not do: a peer that names {@code rsa-oaep} has claimed the modern
     * algorithm, and an SHA-1 mask generation function inside it is a downgrade of that claim rather
     * than a peer honestly asking for an old algorithm. Allowing RSA-1.5 is not a reason to accept it.
     */
    @Test
    void anSha1MaskGenerationFunctionIsStillRefusedWhenLegacyAlgorithmsAreAllowed() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);

        Document doc = parseBody();
        Element method = getFirstChildByName(firstByTag(doc, XENC_NS, "EncryptedKey"), XENC_NS, "EncryptionMethod");
        getFirstChildByName(method, XENC11_NS, "MGF")
                .setAttribute("Algorithm", "http://www.w3.org/2009/xmlenc11#mgf1sha1");
        setBody(doc);

        assertFault(decrypter(ALIAS_1, legacyDecrypt()), UNSUPPORTED_ALGORITHM);
    }

    /**
     * Flips the first plaintext octet from {@code expected} to {@code replacement} by XOR-ing the
     * delta into the first octet of the IV, which is what CBC's first block is masked with.
     * <p>
     * Precise rather than "corrupt a byte and see": this is the whole demonstration below, and a
     * random corruption of the first block lands on valid XML often enough to make a test flaky -
     * which is how the property the tests below assert was found in the first place.
     * <p>
     * {@code expected} is verified against the actual plaintext rather than assumed. It is the
     * newline after {@code <soap:Body>} only because of how {@link #PLAINTEXT_BODY} is indented and
     * because the leading whitespace text node is part of what gets encrypted - neither of which
     * this test controls. Without the check, re-indenting that constant would leave
     * {@code aTamperedCbcCiphertextCanGoUndetected} XOR-ing the wrong delta and passing for the
     * wrong reason, i.e. silently no longer asserting its security property.
     */
    private void flipFirstPlaintextOctet(Document doc, char expected, char replacement) throws Exception {
        Element dataCipherValue = dataCipherValueOf(doc);
        byte[] ivAndCiphertext = base64Of(dataCipherValue);

        byte[] plaintext = decryptCbc(new SecretKeySpec(recoverContentEncryptionKey(doc), "AES"), ivAndCiphertext);
        assertEquals(expected, (char) plaintext[0], "the octet this test flips is not the one it assumes");

        ivAndCiphertext[0] ^= (byte) (expected ^ replacement);
        dataCipherValue.setTextContent(Base64.getEncoder().encodeToString(ivAndCiphertext));
        setBody(doc);
    }

    /**
     * Tampering that breaks the XML is caught, and answered with the one indistinguishable fault
     * rather than anything describing the padding - which is what denies an attacker the oracle.
     */
    @Test
    void aTamperedCbcCiphertextThatBreaksTheXmlIsRefused() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_OAEP);
        // A '<' where the leading whitespace was: no name can start with the space that follows.
        flipFirstPlaintextOctet(parseBody(), '\n', '<');

        assertFault(decrypter(ALIAS_1, legacyDecrypt()), FAILED_CHECK);
    }

    /**
     * And tampering that does not break the XML is <i>not</i> caught. This is the point of the
     * warning on {@code allowedLegacyAlgorithms}, asserted rather than left to prose: AES-CBC carries
     * no authentication tag, so an attacker who can modify the ciphertext gets the receiver to
     * accept plaintext the sender never wrote, and no amount of care on this side detects it. Only
     * choosing AES-GCM does - where the equivalent tampering fails the tag, as
     * {@link #aTamperedCiphertextFailsTheAuthenticationTag} shows.
     * <p>
     * If this test ever starts failing, something has begun authenticating CBC ciphertext, and the
     * warning is the thing to revisit.
     */
    @Test
    void aTamperedCbcCiphertextCanGoUndetected() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_OAEP);
        // A space where the newline was: still whitespace, so the fragment still parses.
        flipFirstPlaintextOctet(parseBody(), '\n', ' ');

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, legacyDecrypt()).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    /**
     * The Bleichenbacher countermeasure, observed from outside: an RSA-1.5 key this gateway cannot
     * unwrap produces the same fault as any other decryption failure, because the unwrap does not
     * fail - it continues with a random content encryption key and the content decryption fails
     * instead.
     */
    @Test
    void anRsa15MessageForAnotherRecipientFailsWithTheGenericFault() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        EncryptSecurePart encrypt = encrypt(ALIAS_2, encryptedBodyReference());
        encrypt.setKeyTransportAlgorithm(RSA_1_5);
        encrypter(TRUSTSTORE_KEY2, encrypt).handleRequest(exchange);

        assertFault(decrypter(ALIAS_1, legacyDecrypt()), FAILED_CHECK);
    }

    /** The same, with CBC underneath, where the failure surfaces as a padding error. */
    @Test
    void anRsa15CbcMessageForAnotherRecipientFailsWithTheGenericFault() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        EncryptSecurePart encrypt = encrypt(ALIAS_2, encryptedBodyReference());
        encrypt.setKeyTransportAlgorithm(RSA_1_5);
        encrypt.setDataEncryptionAlgorithm(AES256_CBC);
        encrypter(TRUSTSTORE_KEY2, encrypt).handleRequest(exchange);

        assertFault(decrypter(ALIAS_1, legacyDecrypt()), FAILED_CHECK);
    }

    // ---- CBC padding interoperability ----------------------------------------------------------
    //
    // Every other test here has Membrane on both ends, so whatever padding this gateway writes it
    // also reads, and a mistake round-trips perfectly. XML Encryption defines the final octet of the
    // plaintext as the pad length and leaves the preceding pad octets arbitrary - so a conforming
    // peer may send PKCS#7 padding, all-random padding, or anything between, and all of it has to
    // decrypt here. These tests re-pad a real message's plaintext themselves to prove it.

    private Element dataCipherValueOf(Document doc) {
        return (Element) firstByTag(doc, XENC_NS, "EncryptedData")
                .getElementsByTagNameNS(XENC_NS, "CipherValue").item(0);
    }

    private static byte[] base64Of(Element cipherValue) {
        return Base64.getDecoder().decode(cipherValue.getTextContent().replaceAll("\\s", ""));
    }

    /**
     * The content encryption key of a message addressed to {@link #ALIAS_1}, so that a test can act
     * on the ciphertext the way a peer - or an attacker who obtained the key - would.
     */
    private byte[] recoverContentEncryptionKey(Document doc) throws Exception {
        Element encryptedKey = firstByTag(doc, XENC_NS, "EncryptedKey");
        byte[] wrapped = base64Of((Element) encryptedKey.getElementsByTagNameNS(XENC_NS, "CipherValue").item(0));
        return rsaOaepCipher(Cipher.DECRYPT_MODE, privateKey(ALIAS_1)).doFinal(wrapped);
    }

    /**
     * Re-encrypts a CBC message's own plaintext with padding this test controls, under the content
     * encryption key the message already carries.
     * <p>
     * The {@code xenc:EncryptedKey} is left untouched, so what arrives is a message whose key
     * material is authentic and whose ciphertext is padded the way some other implementation pads -
     * which is exactly the inbound case that cannot be produced by encrypting with this gateway.
     *
     * @param padder given the plaintext, returns the block-aligned bytes to encrypt
     */
    private void repadCiphertext(Document doc, UnaryOperator<byte[]> padder) throws Exception {
        byte[] cek = recoverContentEncryptionKey(doc);

        Element dataCipherValue = dataCipherValueOf(doc);
        byte[] plaintext = decryptCbc(new SecretKeySpec(cek, "AES"), base64Of(dataCipherValue));

        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(padder.apply(plaintext));

        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        dataCipherValue.setTextContent(Base64.getEncoder().encodeToString(out));
        setBody(doc);
    }

    /** Appends {@code padLength} octets, the last of which states the length, the rest as given. */
    private static byte[] padWith(byte[] plaintext, int padLength, IntUnaryOperator fill) {
        byte[] padded = new byte[plaintext.length + padLength];
        System.arraycopy(plaintext, 0, padded, 0, plaintext.length);
        for (int i = 0; i < padLength - 1; i++) {
            padded[plaintext.length + i] = (byte) fill.applyAsInt(i);
        }
        padded[padded.length - 1] = (byte) padLength;
        return padded;
    }

    private static int padLengthFor(byte[] plaintext) {
        return 16 - plaintext.length % 16;
    }

    /** PKCS#7: every pad octet equals the length. A conforming, and very common, choice. */
    @Test
    void ciphertextPaddedThePkcs7WayDecrypts() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_OAEP);
        repadCiphertext(parseBody(), plaintext -> {
            int padLength = padLengthFor(plaintext);
            return padWith(plaintext, padLength, i -> padLength);
        });

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, legacyDecrypt()).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    /**
     * ISO 10126: the pad octets before the length are random. This is what Apache Santuario and
     * WSS4J write, and it is why decrypting with {@code PKCS5Padding} - which additionally requires
     * every pad octet to equal the length - would reject a conforming peer.
     */
    @Test
    void ciphertextPaddedWithRandomOctetsDecrypts() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_OAEP);
        SecureRandom random = new SecureRandom();
        repadCiphertext(parseBody(), plaintext ->
                padWith(plaintext, padLengthFor(plaintext), i -> random.nextInt(256)));

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, legacyDecrypt()).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    /**
     * A full block of padding, which is what a conforming peer sends when the plaintext already ends
     * on a block boundary - the case an off-by-one in the unpadding gets wrong.
     * <p>
     * The plaintext is brought to a boundary with spaces rather than with a first round of padding:
     * those spaces survive the unpadding as character data, and whitespace between elements is
     * ignorable, whereas the NUL octets a pad block is free to contain are not legal XML characters
     * at all and would fail the fragment parse for the wrong reason.
     */
    @Test
    void ciphertextPaddedWithAFullBlockDecrypts() throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_OAEP);
        repadCiphertext(parseBody(), plaintext -> {
            byte[] aligned = new byte[plaintext.length + (16 - plaintext.length % 16) % 16];
            Arrays.fill(aligned, (byte) ' ');
            System.arraycopy(plaintext, 0, aligned, 0, plaintext.length);
            return padWith(aligned, 16, i -> 0);
        });

        assertEquals(Outcome.CONTINUE, decrypter(ALIAS_1, legacyDecrypt()).handleRequest(exchange));

        assertBodyIsPlaintextAgain();
    }

    /**
     * A final octet that is not a possible pad length, which is what a tampered or mis-keyed
     * ciphertext most often decrypts to. The answer is the same generic fault as every other
     * failure - the padding must not be observable.
     */
    @ParameterizedTest
    @ValueSource(ints = {0x00, 0x11})
    void ciphertextWithAnImpossiblePadLengthFailsWithTheGenericFault(int finalOctet) throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptForWith(AES256_CBC, RSA_OAEP);
        repadCiphertext(parseBody(), plaintext -> {
            byte[] padded = padWith(plaintext, padLengthFor(plaintext), i -> 0);
            padded[padded.length - 1] = (byte) finalOctet;
            return padded;
        });

        assertFault(decrypter(ALIAS_1, legacyDecrypt()), FAILED_CHECK);
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

    @ParameterizedTest
    @CsvSource({
            "http://schemas.xmlsoap.org/soap/envelope/, actor, false",
            "http://schemas.xmlsoap.org/soap/envelope/, actor, true",
            "http://www.w3.org/2003/05/soap-envelope, role, false",
            "http://www.w3.org/2003/05/soap-envelope, role, true"
    })
    void ciphertextForAnotherActorPassesThrough(String soapNs, String actorAttribute, boolean explicitActor)
            throws Exception {
        exchangeWithBody(PLAINTEXT_BODY.replace(SOAP_NS, soapNs));
        encryptFor(ALIAS_1);
        Document doc = parseBody();
        Element security = firstByTag(doc, WsSecurityXmlUtil.WSSE_NS, "Security");
        if (explicitActor) {
            security.setAttributeNS(soapNs, "soap:" + actorAttribute, "urn:gateway");
        }
        Element foreign = doc.createElementNS(WsSecurityXmlUtil.WSSE_NS, "wsse:Security");
        if (!explicitActor) {
            foreign.setAttributeNS(soapNs, "soap:" + actorAttribute, "urn:backend");
        }
        Element ciphertext = (Element) firstByTag(doc, XENC_NS, "EncryptedData").cloneNode(true);
        ciphertext.setAttribute("Id", "ED-other-recipient");
        foreign.appendChild(ciphertext);
        firstByTag(doc, soapNs, "Header").appendChild(foreign);
        setBody(doc);
        Element before = WsSecurityXmlUtil.childElementsOf(firstByTag(parseBody(), soapNs, "Header")).getLast();

        WsSecurityInterceptor receiver = validating(decrypt(encryptedBodyReference()));
        receiver.setKeyStore(signingKeyStore(ALIAS_1));
        if (explicitActor) {
            receiver.setActor("urn:gateway");
        }
        receiver.init(router);
        assertEquals(Outcome.CONTINUE, receiver.handleRequest(exchange));

        Document result = parseBody();
        assertEquals("bar", result.getElementsByTagName("foo").item(0).getTextContent());
        assertTrue(before.isEqualNode(firstByTag(result, WsSecurityXmlUtil.WSSE_NS, "Security")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"owned-header", "body"})
    void aSecurityElementCannotHideUnreferencedCiphertext(String location) throws Exception {
        exchangeWithBody(PLAINTEXT_BODY);
        encryptFor(ALIAS_1);
        Document doc = parseBody();
        Element stray = (Element) firstByTag(doc, XENC_NS, "EncryptedData").cloneNode(true);
        stray.setAttribute("Id", "ED-unreferenced");
        Element container = firstByTag(doc, WsSecurityXmlUtil.WSSE_NS, "Security");
        if (location.equals("body")) {
            container = doc.createElementNS(WsSecurityXmlUtil.WSSE_NS, "wsse:Security");
            container.setAttributeNS(SOAP_NS, "soap:actor", "urn:backend");
            firstByTag(doc, SOAP_NS, "Body").appendChild(container);
        }
        container.appendChild(stray);
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
    void decryptTakesNoPerAlgorithmAttributes() {
        // What a peer may send is not a per-algorithm setting: the only way to widen the inbound set
        // is allowedLegacyAlgorithms, which names individual supported legacy algorithms. A
        // free-form algorithm attribute here would let any URI in.
        assertTrue(java.util.Arrays.stream(DecryptValidatePart.class.getMethods())
                .noneMatch(m -> m.getName().equals("setDataEncryptionAlgorithm")
                                || m.getName().equals("setKeyTransportAlgorithm")));
    }

    @Test
    void legacyAlgorithmsAreOffByDefault() {
        assertTrue(new DecryptValidatePart().getAllowedLegacyAlgorithms().isEmpty());
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
