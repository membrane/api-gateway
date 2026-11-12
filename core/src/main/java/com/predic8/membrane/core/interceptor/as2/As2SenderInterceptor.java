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

package com.predic8.membrane.core.interceptor.as2;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.Key;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.as2.As2Keys.checkAs2Id;
import static com.predic8.membrane.core.util.ExtensionLoader.load;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;

/**
 * @description <p>AS2 (RFC 4130) is a protocol for exchanging business documents such as EDI messages over HTTP.
 * Messages are signed, encrypted and compressed with S/MIME, and the receiver confirms each one with a signed
 * receipt, the MDN. The <code>as2Sender</code> delivers documents to a trading partner over AS2.</p>
 *
 * <p>Internal systems post the plain document, e.g. an EDIFACT order, to the API. The interceptor turns the request
 * into an AS2 message to the partner and sends it to the <code>target</code> of the API, the AS2 URL of the partner.
 * The Content-Type of the request becomes the content type of the document, the filename of a Content-Disposition
 * header its file name. The other request headers are not sent to the partner.</p>
 *
 * <p>The interceptor verifies the MDN of the partner: its signature against the partner's certificate, that it
 * refers to the message, and the MIC, which proves that the partner received the document unchanged. The client
 * gets status 200 with the Message-ID, the disposition and the MIC as JSON if the partner confirmed the message,
 * otherwise status 502 with the reason. Only synchronous MDNs in the HTTP response are supported.</p>
 *
 * <p>Put the interceptor directly into the <code>flow</code> of the API, not into <code>request</code>, otherwise
 * the MDN is not checked. Interceptors after it must not change the request body: the signature covers the exact
 * bytes.</p>
 *
 * <p>The interceptor needs the Membrane AS2 Extension. Without the extension Membrane does not start.
 * Contact predic8 for the extension.</p>
 *
 * <pre>
 * as2Sender:
 *   as2Id: &lt;own AS2 ID&gt;
 *   keystore: ... | key: ...
 *   partner:
 *     as2Id: &lt;AS2 ID of the partner&gt;
 *     certificate:
 *       location: &lt;PEM file&gt;
 *   [ signatureDigest: sha1 | sha256 | sha384 | sha512 ]
 *   [ encryption: none | triple_des | aes128_cbc | aes192_cbc | aes256_cbc ]
 *   [ compression: none | before_signing | after_signing ]
 *   [ mdn: signed | unsigned | none ]
 *   [ subject: &lt;subject&gt; ]
 *   [ dispositionNotificationTo: &lt;address&gt; ]
 *   [ messageIdDomain: &lt;domain&gt; ]
 *   [ maxPayloadSize: &lt;bytes&gt; ]
 *   [ insecureAllowExpiredCertificates: true | false ]
 * </pre>
 * @yaml
 * <pre><code>
 * api:
 *   port: 2001
 *   path:
 *     uri: /acme
 *   flow:
 *     - as2Sender:
 *         as2Id: MEMBRANE
 *         keystore:
 *           location: membrane.p12
 *           password: secret
 *           keyAlias: membrane
 *         partner:
 *           as2Id: ACME
 *           certificate:
 *             location: acme.pem
 *         compression: before_signing
 *   target:
 *     url: https://as2.acme.example.com/as2
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "as2Sender")
public class As2SenderInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(As2SenderInterceptor.class);

    private static final String SENDER_CLASS = "io.membrane_api.ext.as2.membrane.As2SenderComponentImpl";

    /**
     * Characters of a domain name. It becomes part of the Message-ID, where '@', '<' or '>' would break it.
     */
    private static final Pattern DOMAIN = Pattern.compile("[A-Za-z0-9.-]+");

    /**
     * Digest algorithm of the signature and the MIC.
     */
    public enum SignatureDigest {
        SHA1, SHA256, SHA384, SHA512
    }

    /**
     * Content encryption algorithm. Triple DES is only for partners that do not support AES.
     */
    public enum Encryption {
        NONE, TRIPLE_DES, AES128_CBC, AES192_CBC, AES256_CBC
    }

    /**
     * Where the message is compressed (RFC 5402).
     */
    public enum Compression {
        NONE, BEFORE_SIGNING, AFTER_SIGNING
    }

    /**
     * Which MDN is requested from the partner.
     */
    public enum Mdn {
        NONE, UNSIGNED, SIGNED
    }

    private String as2Id;
    private KeyStore keyStore;
    private Key key;
    private As2Partner partner;
    private SignatureDigest signatureDigest = SignatureDigest.SHA256;
    private Encryption encryption = Encryption.AES256_CBC;
    private Compression compression = Compression.NONE;
    private Mdn mdn = Mdn.SIGNED;
    private String subject = "AS2 Message";
    private String dispositionNotificationTo;
    private String messageIdDomain = "as2.local";
    private long maxPayloadSize = 100L * 1024 * 1024;
    private boolean insecureAllowExpiredCertificates;

    /**
     * Written in init(), read-only afterwards.
     */
    private As2SenderExtension sender;

    @Override
    public void init() {
        super.init();
        checkAs2Id("as2Id of as2Sender", as2Id);
        if (partner == null)
            throw new ConfigurationException("as2Sender needs a partner.");
        if (maxPayloadSize <= 0)
            throw new ConfigurationException("maxPayloadSize of as2Sender must be greater than 0.");
        checkHeaderValue("subject", subject);
        if (dispositionNotificationTo != null)
            checkHeaderValue("dispositionNotificationTo", dispositionNotificationTo);
        if (messageIdDomain == null || !DOMAIN.matcher(messageIdDomain).matches())
            throw new ConfigurationException("messageIdDomain of as2Sender must be a domain name, e.g. example.com.");

        final var keys = new As2Keys("as2Sender", router.getResolverMap(), getBeanBaseLocation());
        if (insecureAllowExpiredCertificates)
            log.warn("as2Sender {} accepts expired certificates. MDNs signed with the expired certificate of a partner are still accepted.", as2Id);
        final var ownKey = keys.loadOwnKey(keyStore, key);
        keys.checkValidity(log, "The certificate of as2Sender " + as2Id, ownKey.certificate(), insecureAllowExpiredCertificates);
        final var partnerCertificate = keys.loadPartnerCertificate(partner);
        keys.checkValidity(log, "The certificate of partner " + partner.getAs2Id(), partnerCertificate, insecureAllowExpiredCertificates);
        checkEncryption(partnerCertificate.getPublicKey().getAlgorithm());

        final var extension = createSender();
        extension.init(new As2SenderSettings(as2Id, ownKey.privateKey(), ownKey.certificate(), partner.getAs2Id(),
                partnerCertificate, signatureDigest, encryption, compression, mdn, subject,
                dispositionNotificationTo != null ? dispositionNotificationTo : as2Id, messageIdDomain, maxPayloadSize,
                insecureAllowExpiredCertificates));
        sender = extension;
    }

    /**
     * Package-private so that tests can replace the extension with a stub.
     */
    As2SenderExtension createSender() {
        return load(SENDER_CLASS, As2SenderExtension.class,
                "The Membrane AS2 Extension is needed for the as2Sender interceptor. Ask info@predic8.de.");
    }

    /**
     * The AS2 library would reject the value only when the first message is sent.
     */
    private static void checkHeaderValue(final String name, final String value) {
        if (value == null || value.isEmpty())
            throw new ConfigurationException("%s of as2Sender must not be empty.".formatted(name));
        if (!value.chars().allMatch(c -> c >= 32 && c <= 126))
            throw new ConfigurationException("%s of as2Sender must consist of printable ASCII characters.".formatted(name));
    }

    /**
     * The AS2 library encrypts the content key with RSA only.
     */
    private void checkEncryption(final String partnerKeyAlgorithm) {
        if (encryption == Encryption.NONE) {
            log.warn("as2Sender {} sends unencrypted messages to {}. Anyone on the way can read them.", as2Id, partner.getAs2Id());
            return;
        }
        if (!partnerKeyAlgorithm.equals("RSA"))
            throw new ConfigurationException("The certificate of partner %s holds a %s key. Encryption needs an RSA key; set encryption to none if the partner does not need it."
                    .formatted(partner.getAs2Id(), partnerKeyAlgorithm));
    }

    @Override
    public Outcome handleRequest(final Exchange exc) {
        try {
            return sender.send(exc);
        } catch (Exception e) {
            return internalError(exc, e, "Could not create the AS2 message.");
        }
    }

    @Override
    public Outcome handleResponse(final Exchange exc) {
        try {
            sender.handleResponse(exc);
            return CONTINUE;
        } catch (Exception e) {
            return internalError(exc, e, "Could not check the MDN.");
        }
    }

    /**
     * Called when the partner could not be reached or a later interceptor aborted.
     */
    @Override
    public void handleAbort(final Exchange exc) {
        try {
            sender.handleAbort(exc);
        } catch (Exception e) {
            log.error("Could not handle the aborted AS2 exchange.", e);
        }
    }

    private Outcome internalError(final Exchange exc, final Exception e, final String detail) {
        // An unreadable body (e.g. invalid Content-Encoding) is answered by FlowController
        final var bodyFailure = throwableOfType(e, ReadingBodyException.class);
        if (bodyFailure != null)
            throw bodyFailure;
        log.error(detail, e);
        internal(router.getConfiguration().isProduction(), getDisplayName())
                .detail(detail)
                .exception(e)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    @Override
    public String getDisplayName() {
        return "AS2 Sender";
    }

    /**
     * @description Own AS2 ID, sent as <code>AS2-From</code>. 1 to 128 printable ASCII characters.
     * @example MEMBRANE
     */
    @MCAttribute
    public void setAs2Id(String as2Id) {
        this.as2Id = as2Id;
    }

    public String getAs2Id() {
        return as2Id;
    }

    /**
     * @description Keystore with the own private key and certificate, which sign the messages. Set either a keystore
     * or a key. Without <code>keyAlias</code> the first key in the keystore is used.
     */
    @MCChildElement(order = 0)
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * @description Own private key and certificate in PEM format, which sign the messages. Set either a keystore or a
     * key. The first certificate belongs to the key.
     */
    @MCChildElement(order = 1)
    public void setKey(Key key) {
        this.key = key;
    }

    public Key getKey() {
        return key;
    }

    /**
     * @description Trading partner the messages are sent to. Messages are encrypted with its certificate, and its
     * MDNs have to be signed with it.
     */
    @MCChildElement(order = 2)
    public void setPartner(As2Partner partner) {
        this.partner = partner;
    }

    public As2Partner getPartner() {
        return partner;
    }

    /**
     * @description Digest algorithm of the signature. It also computes the MIC that the partner returns in the MDN.
     * @default sha256
     * @example sha512
     */
    @MCAttribute
    public void setSignatureDigest(SignatureDigest signatureDigest) {
        this.signatureDigest = signatureDigest;
    }

    public SignatureDigest getSignatureDigest() {
        return signatureDigest;
    }

    /**
     * @description Algorithm messages are encrypted with, or <code>none</code>. Encryption needs an RSA certificate of
     * the partner. Use <code>triple_des</code> only for partners that do not support AES.
     * @default aes256_cbc
     * @example aes128_cbc
     */
    @MCAttribute
    public void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }

    public Encryption getEncryption() {
        return encryption;
    }

    /**
     * @description Whether and where messages are compressed: <code>before_signing</code> compresses the document,
     * <code>after_signing</code> the signed document.
     * @default none
     * @example before_signing
     */
    @MCAttribute
    public void setCompression(Compression compression) {
        this.compression = compression;
    }

    public Compression getCompression() {
        return compression;
    }

    /**
     * @description Which MDN is requested from the partner: <code>signed</code>, <code>unsigned</code> or
     * <code>none</code>. Only a signed MDN proves that the partner received the message. Without an MDN, a 2xx status
     * of the partner counts as delivered.
     * @default signed
     * @example unsigned
     */
    @MCAttribute
    public void setMdn(Mdn mdn) {
        this.mdn = mdn;
    }

    public Mdn getMdn() {
        return mdn;
    }

    /**
     * @description Value of the <code>Subject</code> header of the messages.
     * @default AS2 Message
     * @example Orders
     */
    @MCAttribute
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }

    /**
     * @description Value of the <code>Disposition-Notification-To</code> header, which requests the MDN. Usually an
     * email address. Partners rarely use it.
     * @default the own AS2 ID
     * @example as2@example.com
     */
    @MCAttribute
    public void setDispositionNotificationTo(String dispositionNotificationTo) {
        this.dispositionNotificationTo = dispositionNotificationTo;
    }

    public String getDispositionNotificationTo() {
        return dispositionNotificationTo;
    }

    /**
     * @description Domain part of the generated Message-IDs, the part after the <code>@</code>.
     * @default as2.local
     * @example example.com
     */
    @MCAttribute
    public void setMessageIdDomain(String messageIdDomain) {
        this.messageIdDomain = messageIdDomain;
    }

    public String getMessageIdDomain() {
        return messageIdDomain;
    }

    /**
     * @description Maximum size of the document in bytes. Larger requests are rejected with status 413.
     * @default 104857600
     * @example 10485760
     */
    @MCAttribute
    public void setMaxPayloadSize(long maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    public long getMaxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * @description Whether certificates outside their validity period are used, both the own certificate and that of
     * the partner. Insecure: MDNs signed with the expired certificate of the partner are still accepted, even though
     * the partner may no longer protect the key. Use it only for the time a partner needs to renew its certificate.
     * Otherwise Membrane does not start with such a certificate, and while it runs it sends no messages as soon as a
     * certificate expires.
     * @default false
     * @example true
     */
    @MCAttribute
    public void setInsecureAllowExpiredCertificates(boolean insecureAllowExpiredCertificates) {
        this.insecureAllowExpiredCertificates = insecureAllowExpiredCertificates;
    }

    public boolean isInsecureAllowExpiredCertificates() {
        return insecureAllowExpiredCertificates;
    }
}
