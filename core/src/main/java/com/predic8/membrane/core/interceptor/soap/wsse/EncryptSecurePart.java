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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.ConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.INVALID_SECURITY;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.*;

/**
 * @description Encrypts the elements listed in <code>references</code> with XML Encryption, adding
 * an <code>xenc:EncryptedKey</code> to the <code>wsse:Security</code> header and replacing each
 * target with an <code>xenc:EncryptedData</code>. A single freshly generated content encryption key
 * protects every reference of this part; that key is itself encrypted for the recipient, whose
 * certificate is taken from the enclosing <code>wsSecurity</code> element's <code>truststore</code>
 * under <code>recipientAlias</code>.
 * <p>Order relative to <code>signature</code> is meaningful and not fixed. Listed after a
 * <code>signature</code> this produces sign-then-encrypt, and the receiver has to decrypt before it
 * verifies; listed before one it produces encrypt-then-sign, and the receiver has to verify first.
 * Whichever is chosen, the receiver's <code>validate</code> list has to mirror it.</p>
 * <p>Encryption provides confidentiality only — it says nothing about who sent the message, since
 * the recipient's certificate is public. Combine it with a <code>signature</code> when the sender
 * has to be authenticated. See
 * <code>distribution/tutorials/web-services-security/70-Encrypt-And-Decrypt-Body.yaml</code>.</p>
 * @yaml <pre><code>
 * - wsSecurity:
 *     truststore:
 *       location: backend.p12
 *       password: secret
 *     secure:
 *       - encrypt:
 *           recipientAlias: backend
 *           references:
 *             - by: BODY
 * </code></pre>
 */
@MCElement(name = "encrypt", component = false, id = "wsSecurity-encrypt")
public class EncryptSecurePart extends SecurePart {

    private static final String DEFAULT_DATA_ENCRYPTION_ALGORITHM = AES256_GCM;
    private static final String DEFAULT_KEY_TRANSPORT_ALGORITHM = RSA_OAEP;

    /**
     * Only AEAD modes, and only the modern OAEP. AES-CBC as XML Encryption defines it carries no
     * authentication tag, which is what makes a decrypting gateway usable as the padding oracle of
     * the Jager-Somorovsky attack, and RSA-1.5 is Bleichenbacher's. Neither is offered here even
     * though {@code secure} algorithms are otherwise configurable for legacy backends
     * (see {@code signature}, which still offers rsa-sha1), because there is no way to emit them
     * safely - the weakness is in the algorithm, not in who accepts it.
     */
    private static final List<String> SUPPORTED_DATA_ENCRYPTION_ALGORITHMS = List.of(AES128_GCM, AES256_GCM);
    private static final List<String> SUPPORTED_KEY_TRANSPORT_ALGORITHMS = List.of(RSA_OAEP);

    private List<EncryptionReference> references = new ArrayList<>();
    private String recipientAlias;
    private String dataEncryptionAlgorithm = DEFAULT_DATA_ENCRYPTION_ALGORITHM;
    private String keyTransportAlgorithm = DEFAULT_KEY_TRANSPORT_ALGORITHM;
    private KeyIdentifierKeyInfo keyIdentifier;

    private X509Certificate recipientCertificate;

    @Override
    protected void init() {
        validateConfiguration();
        loadRecipientCertificate();
    }

    /**
     * Whether this part encrypts what {@code by} names, so the enclosing element can check that the
     * part creating it is listed before this one.
     */
    boolean references(EncryptionReference.By by) {
        return references.stream().anyMatch(reference -> reference.getBy() == by);
    }

    /**
     * Whether this part replaces the element {@code by} names outright, rather than only its content.
     * A later part referencing that element would then find nothing there.
     */
    boolean replacesElement(EncryptionReference.By by) {
        return references.stream().anyMatch(reference -> reference.getBy() == by
                                                         && reference.getType() == EncryptionReference.Type.ELEMENT);
    }

    private void validateConfiguration() {
        if (parent.getTrustStore() == null) {
            throw new ConfigurationException(
                    "wsSecurity secure/encrypt requires a <truststore> on the enclosing wsSecurity element.");
        }
        if (recipientAlias == null || recipientAlias.isBlank()) {
            throw new ConfigurationException(
                    "wsSecurity secure/encrypt requires a 'recipientAlias' attribute naming the certificate to " +
                    "encrypt for.");
        }
        if (references.isEmpty()) {
            throw new ConfigurationException("wsSecurity secure/encrypt requires at least one <reference> child element.");
        }
        references.forEach(EncryptionReference::validate);
        requireDistinctIds();
        requireDistinctTargets();
        requireSupported("dataEncryptionAlgorithm", dataEncryptionAlgorithm, SUPPORTED_DATA_ENCRYPTION_ALGORITHMS);
        requireSupported("keyTransportAlgorithm", keyTransportAlgorithm, SUPPORTED_KEY_TRANSPORT_ALGORITHMS);
    }

    /**
     * Two references may not carry the same configured {@code id}: the resulting message would hold
     * two {@code xenc:EncryptedData} elements with one {@code Id}, which every receiver worth the
     * name - this gateway's own {@code decrypt} included - rejects as ambiguous.
     */
    private void requireDistinctIds() {
        Set<String> seen = new HashSet<>();
        for (EncryptionReference reference : references) {
            if (reference.getId() != null && !seen.add(reference.getId())) {
                throw new ConfigurationException("wsSecurity secure/encrypt: the reference id \"" + reference.getId() +
                        "\" is used more than once; each xenc:EncryptedData needs its own.");
            }
        }
    }

    /**
     * The same named target may not be referenced twice either: the second reference would find the
     * {@code xenc:EncryptedData} the first left behind - nothing left to encrypt under
     * {@code ELEMENT}, and super-encryption under {@code CONTENT}, which no receiver here accepts.
     * <p>
     * The counterpart across parts is {@code WsSecurityInterceptor}'s ordering check; without this
     * one, that check would be sidestepped by writing both references under a single
     * {@code encrypt}. XPath references are out of scope for both, since two expressions cannot be
     * compared statically.
     */
    private void requireDistinctTargets() {
        Set<EncryptionReference.By> seen = EnumSet.noneOf(EncryptionReference.By.class);
        for (EncryptionReference reference : references) {
            if (reference.getBy() != EncryptionReference.By.XPATH && !seen.add(reference.getBy())) {
                throw new ConfigurationException("wsSecurity secure/encrypt: by: " + reference.getBy() +
                        " is referenced more than once; after the first reference has encrypted it, there is an " +
                        "xenc:EncryptedData in its place and nothing left for the second to encrypt.");
            }
        }
    }

    private static void requireSupported(String attribute, String value, List<String> supported) {
        if (!supported.contains(value)) {
            throw new ConfigurationException("Unsupported " + attribute + " \"" + value +
                    "\" on wsSecurity secure/encrypt. Supported values: " + String.join(", ", supported));
        }
    }

    /**
     * Resolves {@code recipientAlias} in the enclosing element's truststore.
     * <p>
     * Deliberately not {@code KeyStoreUtil.aliasOrThrow}/{@code firstAliasOrThrow}: both gate on
     * {@code isKeyEntry}, which is false for the {@code trustedCertEntry} entries a truststore holds,
     * so neither can find a certificate there at all. There is also no "first alias" fallback on
     * purpose - picking an arbitrary certificate out of a multi-entry truststore would encrypt the
     * message for whoever happened to sort first.
     */
    private void loadRecipientCertificate() {
        try {
            java.security.KeyStore trustStore = StaticSSLContext.openKeyStore(
                    parent.getTrustStore(), null, parent.getRouter().getResolverMap(), parent.beanBaseLocation());
            if (!(trustStore.getCertificate(recipientAlias) instanceof X509Certificate certificate)) {
                throw new ConfigurationException("No certificate with alias \"" + recipientAlias +
                        "\" in the wsSecurity truststore. Available aliases: " +
                        String.join(", ", Collections.list(trustStore.aliases())) + ".");
            }
            checkUsableForKeyEncipherment(certificate);
            recipientCertificate = certificate;
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Could not load the recipient certificate from the wsSecurity truststore.", e);
        }
    }

    /**
     * The checks that a certificate is actually usable to encrypt a key for. Validity is established
     * once, at startup: re-checking per message would turn an expiry into an outage mid-flight, and
     * the signing side loads its key once for the same reason.
     */
    private void checkUsableForKeyEncipherment(X509Certificate certificate) {
        try {
            certificate.checkValidity();
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            throw new ConfigurationException("Recipient certificate \"" + recipientAlias +
                    "\" is not valid at this time: " + e.getMessage(), e);
        }
        if (!(certificate.getPublicKey() instanceof RSAPublicKey)) {
            throw new ConfigurationException("Certificate \"" + recipientAlias +
                    "\" in the wsSecurity truststore does not hold an RSA public key; keyTransportAlgorithm " +
                    keyTransportAlgorithm + " requires one.");
        }
        boolean[] keyUsage = certificate.getKeyUsage();
        // Index 2 is keyEncipherment, 3 dataEncipherment - either one permits encrypting for this
        // certificate. A certificate with no keyUsage extension is unconstrained, so it passes.
        if (keyUsage != null && keyUsage.length > 3 && !keyUsage[2] && !keyUsage[3]) {
            throw new ConfigurationException("Recipient certificate \"" + recipientAlias +
                    "\"'s keyUsage permits neither keyEncipherment nor dataEncipherment.");
        }
    }

    @Override
    void process(WsSecurityContext ctx) throws Exception {
        Document doc = ctx.document();

        List<Element> encryptedDataElements = new ArrayList<>();
        SecretKey contentEncryptionKey = generateContentEncryptionKey(dataEncryptionAlgorithm);

        try {
            for (EncryptionReference reference : references) {
                List<Element> targets = resolveEncryptionReference(doc, ctx.envelope(), ctx.security(), ctx.soapNs(),
                        reference, parent.getXmlConfig());
                requireOneTargetPerConfiguredId(reference, targets);
                for (Element target : targets) {
                    requireEncryptable(target, reference, ctx.soapNs());
                    encryptedDataElements.add(encrypt(doc, target, reference, contentEncryptionKey));
                }
            }
        } catch (WsSecurityXmlUtil.ReferenceResolutionException e) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "Could not resolve encryption reference: " + e.getMessage(), e);
        }

        ctx.security().appendChild(createEncryptedKey(doc, contentEncryptionKey, encryptedDataElements));
    }

    /**
     * A configured {@code id} names one {@code xenc:EncryptedData}, so the reference carrying it has
     * to resolve to exactly one element. An XPath matching several would put that one id on every
     * one of them, leaving a {@code xenc:ReferenceList} that names the same target repeatedly and a
     * {@code "#id"} neither a signature nor a receiver can resolve.
     * <p>
     * Reported here rather than at startup for the reason {@link #requireDistinctTargets} leaves
     * XPath alone: how many elements an expression matches depends on the message.
     */
    private static void requireOneTargetPerConfiguredId(EncryptionReference reference, List<Element> targets) {
        if (reference.getId() != null && targets.size() > 1) {
            throw new ConfigurationException(("wsSecurity secure/encrypt: reference (%s) carries id \"%s\" but " +
                    "matched %d elements, and an id can name only one xenc:EncryptedData. Omit the id to have " +
                    "one generated per match, or narrow the expression to a single element.")
                    .formatted(reference.describe(), reference.getId(), targets.size()));
        }
    }

    /**
     * Refuses a target whose encryption would destroy the message instead of protecting it.
     * <p>
     * Only an XPath reference can get here: {@code by: BODY} and {@code by: USERNAME_TOKEN} name a
     * legal target by construction, and {@code type: ELEMENT} on the body is already refused in
     * configuration. An expression, though, can select the envelope, either of the two elements the
     * envelope consists of, the {@code wsse:Security} header this part is writing its own key into,
     * or an {@code xenc:EncryptedKey} that an earlier part put there - and replacing any of those
     * yields either something that is no longer a SOAP envelope or ciphertext whose key is itself
     * encrypted and therefore unreachable. The one structural target that can be encrypted is the
     * body's <i>content</i>, which is the ordinary case and the default.
     * <p>
     * A {@link ConfigurationException} rather than a {@link WsSecurityFaultException}: what is wrong
     * here is this gateway's own configuration, not the message, so the sender is answered with an
     * internal error and the operator with a logged warning. A {@code wsse:InvalidSecurity} fault
     * would tell a blameless peer their message was the problem.
     */
    private static void requireEncryptable(Element target, EncryptionReference reference, String soapNs) {
        boolean asElement = reference.getType() == EncryptionReference.Type.ELEMENT;
        String selected = null;
        if (!(target.getParentNode() instanceof Element)) {
            selected = "the " + target.getNodeName() + " document element, which nothing can replace";
        } else if (isNamed(target, soapNs, "Body")) {
            selected = asElement ? "the SOAP body, whose replacement would not be a SOAP envelope" : null;
        } else if (isNamed(target, soapNs, "Header")) {
            selected = "the SOAP header, which carries the wsse:Security element";
        } else if (isNamed(target, WSSE_NS, "Security")) {
            selected = "the wsse:Security header this part writes its own xenc:EncryptedKey into";
        } else if (isNamed(target, XENC_NS, "EncryptedKey")) {
            selected = "an xenc:EncryptedKey, the key material a receiver decrypts with";
        }
        if (selected != null) {
            throw new ConfigurationException(("wsSecurity secure/encrypt: reference (%s) selects %s, so " +
                    "encrypting it%s would leave a message no receiver can read.")
                    .formatted(reference.describe(), selected, asElement ? " as a whole ELEMENT" : ""));
        }
    }

    private static boolean isNamed(Element element, String namespace, String localName) {
        return Objects.equals(namespace, element.getNamespaceURI()) && localName.equals(element.getLocalName());
    }

    /**
     * Replaces {@code target} - or its content - with an {@code xenc:EncryptedData}, and returns it.
     */
    private Element encrypt(Document doc, Element target, EncryptionReference reference, SecretKey contentEncryptionKey)
            throws Exception {
        boolean contentOnly = reference.getType() == EncryptionReference.Type.CONTENT;

        byte[] plaintext = contentOnly
                ? serializeForContentEncryption(target)
                : serializeForElementEncryption(target);

        Element encryptedData = createEncryptedData(doc,
                reference.getId() != null ? reference.getId() : "ED-" + UUID.randomUUID(),
                contentOnly ? TYPE_CONTENT : TYPE_ELEMENT,
                dataEncryptionAlgorithm,
                encryptGcm(contentEncryptionKey, plaintext));

        if (contentOnly) {
            while (target.getFirstChild() != null) {
                target.removeChild(target.getFirstChild());
            }
            target.appendChild(encryptedData);
        } else {
            target.getParentNode().replaceChild(encryptedData, target);
        }
        // So that a signature listed after this part can dereference "#id" to the EncryptedData: the
        // enclosing element marked the ids of the document it received, which was before this existed.
        markIdAttribute(encryptedData);
        return encryptedData;
    }

    /**
     * The {@code xenc:EncryptedKey} carrying the content encryption key, wrapped for the recipient,
     * plus the {@code xenc:ReferenceList} naming everything it decrypts.
     */
    private Element createEncryptedKey(Document doc, SecretKey contentEncryptionKey, List<Element> encryptedDataElements)
            throws Exception {
        Cipher cipher = rsaOaepCipher(Cipher.ENCRYPT_MODE, recipientCertificate.getPublicKey());
        Element encryptedKey = createXencElement(doc, "EncryptedKey");
        encryptedKey.setAttribute("Id", "EK-" + UUID.randomUUID());
        encryptedKey.appendChild(createKeyTransportEncryptionMethod(doc, keyTransportAlgorithm));
        encryptedKey.appendChild(createRecipientKeyInfo(doc));
        encryptedKey.appendChild(createCipherData(doc, cipher.doFinal(contentEncryptionKey.getEncoded())));

        Element referenceList = createXencElement(doc, "ReferenceList");
        for (Element encryptedData : encryptedDataElements) {
            Element dataReference = createXencElement(doc, "DataReference");
            dataReference.setAttribute("URI", "#" + encryptedData.getAttribute("Id"));
            referenceList.appendChild(dataReference);
        }
        encryptedKey.appendChild(referenceList);

        markIdAttribute(encryptedKey);
        return encryptedKey;
    }

    /**
     * The {@code ds:KeyInfo} telling the recipient which of its own keys decrypts this message.
     * <p>
     * A thumbprint {@code wsse:KeyIdentifier} by default, which is what WSS4J and CXF emit for
     * encryption: the recipient already holds the certificate, so naming it by hash says everything
     * that is needed and sending the certificate itself back would only be noise.
     */
    private Element createRecipientKeyInfo(Document doc) throws Exception {
        KeyIdentifierKeyInfo.ValueType valueType = keyIdentifier != null
                ? keyIdentifier.valueTypeOrDefault(KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1)
                : KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1;
        return createKeyInfoWithSecurityTokenReference(doc,
                createKeyIdentifier(doc, recipientCertificate, valueType));
    }

    public List<EncryptionReference> getReferences() {
        return references;
    }

    /**
     * @description The elements to encrypt. Each becomes one <code>xenc:EncryptedData</code>, named
     * by one <code>xenc:DataReference</code> in this part's <code>xenc:EncryptedKey</code> — except
     * an <code>XPATH</code> reference matching more than one element, which becomes one
     * <code>xenc:EncryptedData</code> per matched element.
     */
    @MCChildElement(order = 1)
    public void setReferences(List<EncryptionReference> references) {
        this.references = references == null ? List.of() : List.copyOf(references);
    }

    public String getRecipientAlias() {
        return recipientAlias;
    }

    /**
     * @description The alias of the recipient's certificate in the enclosing <code>wsSecurity</code>
     * element's <code>truststore</code>. Its public key encrypts the content encryption key, so only
     * the holder of the matching private key can read the message. Required: there is no default,
     * because picking a certificate automatically would mean encrypting for an arbitrary recipient.
     * A <code>PEM</code> truststore exposes its entries as <code>cert-0</code>,
     * <code>cert-1</code>, and so on.
     * @example backend
     */
    @MCAttribute
    public void setRecipientAlias(String recipientAlias) {
        this.recipientAlias = recipientAlias;
    }

    public String getDataEncryptionAlgorithm() {
        return dataEncryptionAlgorithm;
    }

    /**
     * @description The XML Encryption algorithm URI protecting the referenced elements. Only the
     * authenticated AES-GCM modes are supported — <code>http://www.w3.org/2009/xmlenc11#aes128-gcm</code>
     * and <code>http://www.w3.org/2009/xmlenc11#aes256-gcm</code>. The CBC modes are refused because
     * they carry no authentication tag, which leaves a decrypting receiver usable as a padding oracle.
     * @default http://www.w3.org/2009/xmlenc11#aes256-gcm
     */
    @MCAttribute
    public void setDataEncryptionAlgorithm(String dataEncryptionAlgorithm) {
        this.dataEncryptionAlgorithm = dataEncryptionAlgorithm;
    }

    public String getKeyTransportAlgorithm() {
        return keyTransportAlgorithm;
    }

    /**
     * @description The algorithm URI encrypting the content encryption key for the recipient. Only
     * <code>http://www.w3.org/2009/xmlenc11#rsa-oaep</code> with SHA-256 and MGF1-SHA-256 is
     * supported; RSA-1.5 is refused as a Bleichenbacher oracle, and the older
     * <code>rsa-oaep-mgf1p</code> URI cannot express a SHA-256 mask generation function.
     * @default http://www.w3.org/2009/xmlenc11#rsa-oaep
     */
    @MCAttribute
    public void setKeyTransportAlgorithm(String keyTransportAlgorithm) {
        this.keyTransportAlgorithm = keyTransportAlgorithm;
    }

    public KeyIdentifierKeyInfo getKeyIdentifier() {
        return keyIdentifier;
    }

    /**
     * @description Names the recipient by a <code>wsse:SecurityTokenReference</code>/<code>wsse:KeyIdentifier</code>,
     * which is what the <code>xenc:EncryptedKey</code> carries whether or not this element is present:
     * omitted, it names the certificate by its SHA-1 thumbprint. Configure it explicitly only to select
     * <code>valueType=X509_V3</code>, which embeds the certificate itself — the one reason being a
     * recipient that cannot look a thumbprint up in a store of its own. Unlike under
     * <code>signature</code>, there is no <code>x509Data</code> alternative here, because the two would
     * produce the same <code>ds:KeyInfo</code>.
     */
    @MCChildElement(order = 2)
    public void setKeyIdentifier(KeyIdentifierKeyInfo keyIdentifier) {
        this.keyIdentifier = keyIdentifier;
    }
}
