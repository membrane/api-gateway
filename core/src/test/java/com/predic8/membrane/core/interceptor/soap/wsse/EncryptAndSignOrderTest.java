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
import org.w3c.dom.Document;

import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.FAILED_CHECK;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.XENC_NS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The interaction between {@code signature} and {@code encrypt}, which WS-SecurityPolicy leaves to
 * the sender: both sign-before-encrypt and encrypt-before-sign are legal, and the receiver has to
 * mirror whichever was used.
 * <p>
 * Every case crosses a real serialize/re-parse boundary between sender and receiver. That is not
 * ceremony: encryption moves a subtree out of the document and back, and whether the restored form
 * canonicalizes to what was signed depends on namespace declarations that only exist once the
 * message has actually been written out and read back. An in-process hand-off would share one DOM
 * and prove nothing about the wire.
 */
class EncryptAndSignOrderTest extends AbstractWsSecurityTest {

    private static final String BODY = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                           xmlns:biz="http://example.com/business">
                <soap:Body><biz:order><biz:item>widget</biz:item></biz:order></soap:Body>
            </soap:Envelope>
            """;

    /** Both roles use key1: it signs, and it is also the recipient the message is encrypted for. */
    private WsSecurityInterceptor sender(SecurePart... parts) {
        return wsSecurityWithBothStores(ALIAS_1, TRUSTSTORE, List.of(), List.of(parts));
    }

    private WsSecurityInterceptor receiver(ValidatePart... parts) {
        return wsSecurityWithBothStores(ALIAS_1, TRUSTSTORE, List.of(parts), List.of());
    }

    private void send(SecurePart... parts) throws Exception {
        exchangeWithBody(BODY);
        assertEquals(Outcome.CONTINUE, sender(parts).handleRequest(exchange));
        crossTheWire();
    }

    private static SignatureSecurePart signBody() {
        return signature(bodyReference());
    }

    private static EncryptSecurePart encryptBody() {
        return encrypt(ALIAS_1, encryptedBodyReference());
    }

    // ---- sign, then encrypt --------------------------------------------------------------------

    /**
     * The signature covers {@code soap:Body} by its {@code wsu:Id}, and content encryption leaves both
     * the element and that id in place - so the reference still resolves, and once the content is
     * back the digest matches again.
     */
    @Test
    void signThenEncryptVerifiesWhenTheReceiverDecryptsFirst() throws Exception {
        send(signBody(), encryptBody());

        assertEquals(Outcome.CONTINUE,
                receiver(decrypt(encryptedBodyReference()), requiring(bodyReference())).handleRequest(exchange));

        Document doc = parseBody();
        assertEquals("widget", doc.getElementsByTagNameNS("http://example.com/business", "item")
                .item(0).getTextContent());
    }

    /** Verifying first digests ciphertext where the signer digested plaintext. */
    @Test
    void signThenEncryptFailsWhenTheReceiverVerifiesFirst() throws Exception {
        send(signBody(), encryptBody());

        assertFault(receiver(requiring(bodyReference()), decrypt()), FAILED_CHECK);
    }

    // ---- encrypt, then sign --------------------------------------------------------------------

    /** Here the digest was taken over the ciphertext, so it has to be checked before decryption. */
    @Test
    void encryptThenSignVerifiesWhenTheReceiverVerifiesFirst() throws Exception {
        send(encryptBody(), signBody());

        assertEquals(Outcome.CONTINUE,
                receiver(requiring(bodyReference()), decrypt(encryptedBodyReference())).handleRequest(exchange));

        assertEquals("widget", parseBody().getElementsByTagNameNS("http://example.com/business", "item")
                .item(0).getTextContent());
    }

    /** Decrypting first replaces the very bytes the signature covered. */
    @Test
    void encryptThenSignFailsWhenTheReceiverDecryptsFirst() throws Exception {
        send(encryptBody(), signBody());

        assertFault(receiver(decrypt(), requiring(bodyReference())), FAILED_CHECK);
    }

    // ---- covering the key material -------------------------------------------------------------

    @Test
    void aSignatureCanCoverTheEncryptedKeyWhenListedAfterTheEncrypt() throws Exception {
        send(encryptBody(), signature(bodyReference(), reference(SignatureReference.By.ENCRYPTED_KEY)));

        Document doc = parseBody();
        String encryptedKeyId = firstByTag(doc, XENC_NS, "EncryptedKey").getAttribute("Id");
        assertTrue(rawBody().contains("#" + encryptedKeyId),
                "a ds:Reference must name the xenc:EncryptedKey");
        assertEquals(Outcome.CONTINUE, receiver(
                requiring(bodyReference(), reference(SignatureReference.By.ENCRYPTED_KEY)),
                decrypt(encryptedBodyReference())).handleRequest(exchange));
    }

    /** Nothing to sign yet: the key the reference names does not exist until the encrypt runs. */
    @Test
    void aSignatureCoveringTheEncryptedKeyBeforeTheEncryptIsAConfigurationError() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> sender(signature(reference(SignatureReference.By.ENCRYPTED_KEY)), encryptBody()));
        assertTrue(e.getMessage().contains("ENCRYPTED_KEY"), e.getMessage());
        assertTrue(e.getMessage().contains("after"), e.getMessage());
    }

    // ---- an element-encrypted target is gone for anything listed later -------------------------

    /**
     * The mirror-image ordering error: element encryption removes the UsernameToken outright, so a
     * signature listed after it would have nothing left to reference.
     */
    @Test
    void aSignatureCoveringATokenAnEncryptRemovesIsAConfigurationError() {
        EncryptSecurePart encrypt = encrypt(ALIAS_1,
                encryptionReference(EncryptionReference.By.USERNAME_TOKEN, EncryptionReference.Type.ELEMENT));

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> sender(usernameToken(), encrypt, signature(reference(SignatureReference.By.USERNAME_TOKEN))));
        assertTrue(e.getMessage().contains("USERNAME_TOKEN"), e.getMessage());
        assertTrue(e.getMessage().contains("before"), e.getMessage());
    }

    /** Signing the token before it is encrypted is the legal way round, and stays legal. */
    @Test
    void signingATokenBeforeElementEncryptingItIsAccepted() throws Exception {
        EncryptSecurePart encrypt = encrypt(ALIAS_1,
                encryptionReference(EncryptionReference.By.USERNAME_TOKEN, EncryptionReference.Type.ELEMENT));

        assertDoesNotThrow(() ->
                sender(usernameToken(), signature(reference(SignatureReference.By.USERNAME_TOKEN)), encrypt));
    }

    /** The gap the encrypt-side created-by map closes: the token has to exist before it is encrypted. */
    @Test
    void anEncryptCoveringTheUsernameTokenBeforeItIsCreatedIsAConfigurationError() {
        EncryptSecurePart encrypt = encrypt(ALIAS_1,
                encryptionReference(EncryptionReference.By.USERNAME_TOKEN, EncryptionReference.Type.ELEMENT));

        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> sender(encrypt, usernameToken()));
        assertTrue(e.getMessage().contains("USERNAME_TOKEN"), e.getMessage());
        assertTrue(e.getMessage().contains("encrypt"), e.getMessage());
    }

    private static UsernameTokenSecurePart usernameToken() {
        UsernameTokenSecurePart token = new UsernameTokenSecurePart();
        token.setUsername("alice");
        token.setPassword("secret");
        return token;
    }
}
