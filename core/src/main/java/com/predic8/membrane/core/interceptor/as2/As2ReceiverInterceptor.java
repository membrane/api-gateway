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
import com.predic8.membrane.core.interceptor.as2.As2Keys.OwnKey;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.as2.As2Keys.checkAs2Id;
import static com.predic8.membrane.core.interceptor.as2.As2Keys.warnIfExpired;
import static com.predic8.membrane.core.util.ExtensionLoader.load;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;

/**
 * @description <p>AS2 (RFC 4130) is a protocol for exchanging business documents such as EDI messages over HTTP.
 * Messages are signed, encrypted and compressed with S/MIME, and the receiver confirms each one with a signed
 * receipt, the MDN. The <code>as2Receiver</code> makes an API an AS2 endpoint.</p>
 *
 * <p>The interceptor decrypts, decompresses and verifies incoming messages and forwards the payload, e.g. the EDI
 * document, to the backend with the Content-Type of the payload. When the backend has answered, the interceptor
 * replaces its response with the MDN. The MDN reports the message as processed if the backend answered with a
 * 2xx status, otherwise as failed. It also reports the message as failed if an interceptor after the
 * <code>as2Receiver</code> fails while handling the response, even if the backend accepted the payload. A message
 * that fails verification is not forwarded: the sender gets an error MDN, or status 400 or 403 if it did not
 * request an MDN. A message that is too large gets status 413. Only synchronous MDNs in the HTTP response are
 * supported.</p>
 *
 * <p>Put the interceptor directly into the <code>flow</code> of the API, not into <code>request</code>, otherwise
 * no MDN is sent. Interceptors that change the body must not run before it: the signature covers the exact bytes.</p>
 *
 * <p>The interceptor needs the Membrane AS2 Extension. Without the extension Membrane does not start.
 * Contact predic8 for the extension.</p>
 *
 * <pre>
 * as2Receiver:
 *   as2Id: &lt;own AS2 ID&gt;
 *   keystore: ... | key: ...
 *   partners:
 *     - as2Id: &lt;AS2 ID of the partner&gt;
 *       certificate:
 *         location: &lt;PEM file&gt;
 *     ...
 *   [ requireSigned: true | false ]
 *   [ requireEncrypted: true | false ]
 *   [ maxPayloadSize: &lt;bytes&gt; ]
 * </pre>
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   path:
 *     uri: /as2
 *   flow:
 *     - as2Receiver:
 *         as2Id: MEMBRANE
 *         keystore:
 *           location: membrane.p12
 *           password: secret
 *           keyAlias: membrane
 *         partners:
 *           - as2Id: ACME
 *             certificate:
 *               location: acme.pem
 *         requireEncrypted: true
 *   target:
 *     url: http://erp.example.com/inbound
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "as2Receiver")
public class As2ReceiverInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(As2ReceiverInterceptor.class);

    private static final String RECEIVER_CLASS = "io.membrane_api.ext.as2.membrane.As2ReceiverComponentImpl";

    private String as2Id;
    private KeyStore keyStore;
    private Key key;
    private List<As2Partner> partners = new ArrayList<>();
    private boolean requireSigned = true;
    private boolean requireEncrypted;
    private long maxPayloadSize = 100L * 1024 * 1024;

    /**
     * Written in init(), read-only afterwards.
     */
    private As2ReceiverExtension receiver;

    @Override
    public void init() {
        super.init();
        checkAs2Id("as2Id of as2Receiver", as2Id);
        if (partners.isEmpty())
            throw new ConfigurationException("as2Receiver needs at least one partner.");
        if (maxPayloadSize <= 0)
            throw new ConfigurationException("maxPayloadSize of as2Receiver must be greater than 0.");

        final var keys = new As2Keys("as2Receiver", router.getResolverMap(), getBeanBaseLocation());
        final var ownKey = keys.loadOwnKey(keyStore, key);
        checkOwnKey(ownKey);
        final var partnerCertificates = loadPartners(keys);
        if (!requireSigned)
            log.warn("as2Receiver {} accepts unsigned messages. Their sender is not authenticated: anyone can send them in the name of a partner.", as2Id);

        final var extension = createReceiver();
        extension.init(new As2ReceiverSettings(as2Id, ownKey.privateKey(), ownKey.certificate(), partnerCertificates,
                requireSigned, requireEncrypted, maxPayloadSize));
        receiver = extension;
    }

    /**
     * Package-private so that tests can replace the extension with a stub.
     */
    As2ReceiverExtension createReceiver() {
        return load(RECEIVER_CLASS, As2ReceiverExtension.class,
                "The Membrane AS2 Extension is needed for the as2Receiver interceptor. Ask info@predic8.de.");
    }

    /**
     * The AS2 library decrypts only with RSA keys.
     */
    private void checkOwnKey(final OwnKey ownKey) {
        if (!ownKey.isRsa()) {
            if (requireEncrypted)
                throw new ConfigurationException("as2Receiver requires encrypted messages, but its key is an EC key. Decryption needs an RSA key.");
            log.warn("The key of as2Receiver {} is an EC key. Partners cannot send encrypted messages: decryption needs an RSA key.", as2Id);
        }
        warnIfExpired(log, "The certificate of as2Receiver " + as2Id, ownKey.certificate());
    }

    private Map<String, X509Certificate> loadPartners(final As2Keys keys) {
        final var certificates = new LinkedHashMap<String, X509Certificate>();
        for (final var partner : partners) {
            final var certificate = keys.loadPartnerCertificate(partner);
            if (certificates.containsKey(partner.getAs2Id()))
                throw new ConfigurationException("as2Receiver has more than one partner with the AS2 ID %s.".formatted(partner.getAs2Id()));
            warnIfExpired(log, "The certificate of partner " + partner.getAs2Id(), certificate);
            certificates.put(partner.getAs2Id(), certificate);
        }
        return certificates;
    }

    @Override
    public Outcome handleRequest(final Exchange exc) {
        try {
            return receiver.receive(exc);
        } catch (Exception e) {
            return internalError(exc, e, "Could not process the AS2 message.");
        }
    }

    /**
     * The backend accepted the payload only with a 2xx status.
     */
    @Override
    public Outcome handleResponse(final Exchange exc) {
        try {
            receiver.respond(exc, exc.getResponse().isOk());
            return CONTINUE;
        } catch (Exception e) {
            return internalError(exc, e, "Could not create the MDN.");
        }
    }

    /**
     * Called when the backend could not be reached or a later interceptor aborted. The backend's acceptance cannot be
     * confirmed then: in the response flow the backend's response has already been replaced by the error response.
     */
    @Override
    public void handleAbort(final Exchange exc) {
        try {
            receiver.respond(exc, false);
        } catch (Exception e) {
            log.error("Could not create the MDN for an aborted exchange.", e);
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
        return "AS2 Receiver";
    }

    /**
     * @description Own AS2 ID. The <code>AS2-To</code> header of incoming messages has to match it, and MDNs are
     * sent from it. 1 to 128 printable ASCII characters.
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
     * @description Keystore with the own private key and certificate, which decrypt incoming messages and sign MDNs.
     * Set either a keystore or a key. Without <code>keyAlias</code> the first key in the keystore is used.
     */
    @MCChildElement(order = 0)
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * @description Own private key and certificate in PEM format, which decrypt incoming messages and sign MDNs.
     * Set either a keystore or a key. The first certificate belongs to the key.
     */
    @MCChildElement(order = 1)
    public void setKey(Key key) {
        this.key = key;
    }

    public Key getKey() {
        return key;
    }

    /**
     * @description Trading partners that may send messages. A message is assigned to a partner by its
     * <code>AS2-From</code> header, and its signature is verified only against that partner's certificate.
     * Messages from other senders are rejected.
     */
    @MCChildElement(order = 2)
    public void setPartners(List<As2Partner> partners) {
        this.partners = partners;
    }

    public List<As2Partner> getPartners() {
        return partners;
    }

    /**
     * @description Whether unsigned messages are rejected. The sender of an unsigned message is not authenticated,
     * so anyone can send one in the name of a partner.
     * @default true
     * @example false
     */
    @MCAttribute
    public void setRequireSigned(boolean requireSigned) {
        this.requireSigned = requireSigned;
    }

    public boolean isRequireSigned() {
        return requireSigned;
    }

    /**
     * @description Whether unencrypted messages are rejected. Decryption needs an RSA key.
     * @default false
     * @example true
     */
    @MCAttribute
    public void setRequireEncrypted(boolean requireEncrypted) {
        this.requireEncrypted = requireEncrypted;
    }

    public boolean isRequireEncrypted() {
        return requireEncrypted;
    }

    /**
     * @description Maximum size of the payload in bytes. It also limits decompression, so a small compressed
     * message cannot expand into a huge payload. Larger messages are rejected. Requests with a body larger than
     * twice this size plus 1 MB are rejected before decryption.
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
}
