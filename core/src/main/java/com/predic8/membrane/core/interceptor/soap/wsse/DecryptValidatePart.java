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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.security.KeyStoreUtil;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.util.*;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.*;

/**
 * @description Decrypts the XML Encryption content of the inbound message: the
 * <code>xenc:EncryptedKey</code> in the <code>wsse:Security</code> header is opened with the
 * enclosing <code>wsSecurity</code> element's <code>keystore</code> private key, and every
 * <code>xenc:EncryptedData</code> it names is replaced by its plaintext.
 * <p>Accepted algorithms are fixed and deliberately narrower than what <code>encrypt</code> can be
 * configured to produce: AES-128/256-GCM for content and RSA-OAEP with SHA-256 and MGF1-SHA-256 for
 * the key. A message using anything else — an AES-CBC downgrade, RSA-1.5, or the modern OAEP URI
 * carrying an SHA-1 mask generation function — is answered with
 * <code>wsse:UnsupportedAlgorithm</code> before any cipher is constructed. That asymmetry is the
 * point: accepting what is merely common would let any peer choose the padding-oracle-shaped
 * algorithms whatever this gateway emits.</p>
 * <p>Without <code>requiredReferences</code> this asserts only that whatever arrived encrypted
 * could be decrypted — not that anything was encrypted at all, so a peer sending an entirely
 * plaintext message would pass. List the elements that must have arrived encrypted to make
 * confidentiality enforceable. See
 * <code>distribution/tutorials/web-services-security/70-Encrypt-And-Decrypt-Body.yaml</code>.</p>
 * @yaml <pre><code>
 * - wsSecurity:
 *     keystore:
 *       location: backend.p12
 *       password: secret
 *       keyAlias: backend
 *     validate:
 *       - decrypt:
 *           requiredReferences:
 *             - by: BODY
 * </code></pre>
 */
@MCElement(name = "decrypt", component = false, id = "wsSecurity-validate-decrypt")
public class DecryptValidatePart extends ValidatePart {

    private static final Logger log = LoggerFactory.getLogger(DecryptValidatePart.class);

    /**
     * Only the authenticated GCM modes. XML Encryption's CBC modes carry no authentication tag, which
     * is exactly what makes a decrypting gateway usable as the oracle in the Jager-Somorovsky
     * backwards-compatibility attack.
     */
    private static final Set<String> ALLOWED_DATA_ALGORITHMS = Set.of(AES128_GCM, AES256_GCM);
    /** RSA-1.5 is absent by intent: it is Bleichenbacher's oracle. */
    private static final Set<String> ALLOWED_KEY_TRANSPORT_ALGORITHMS = Set.of(RSA_OAEP);
    private static final Set<String> ALLOWED_OAEP_DIGESTS = Set.of(SHA256_DIGEST);
    private static final Set<String> ALLOWED_MGF_ALGORITHMS = Set.of(MGF1_SHA256);

    private List<EncryptionReference> requiredReferences = new ArrayList<>();

    private PrivateKey privateKey;

    @Override
    protected void init() {
        if (parent.getKeyStore() == null) {
            throw new ConfigurationException(
                    "wsSecurity validate/decrypt requires a <keystore> on the enclosing wsSecurity element.");
        }
        requiredReferences.forEach(EncryptionReference::validate);
        loadDecryptionKey();
    }

    private void loadDecryptionKey() {
        KeyStore keyStore = parent.getKeyStore();
        try {
            char[] keyPassword = resolveKeyPassword(keyStore);
            java.security.KeyStore ks = StaticSSLContext.openKeyStore(
                    keyStore, keyPassword, parent.getRouter().getResolverMap(), parent.beanBaseLocation());

            String alias = keyStore.getKeyAlias() != null
                    ? KeyStoreUtil.aliasOrThrow(ks, keyStore.getKeyAlias())
                    : KeyStoreUtil.firstAliasOrThrow(ks);

            privateKey = (PrivateKey) ks.getKey(alias, keyPassword);
            if (privateKey == null) {
                throw new ConfigurationException("Keystore alias \"" + alias + "\" holds no private key.");
            }
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Could not load the decryption key from the wsSecurity keystore.", e);
        }
    }

    @Override
    void process(WsSecurityContext ctx) throws Exception {
        Document doc = ctx.document();
        Element security = ctx.security();

        Element encryptedKey = findSingleEncryptedKey(security);
        checkKeyTransportAlgorithm(encryptedKey);

        // After the structural checks, before any decryption. After, so that "there is nothing here
        // to decrypt" is reported as the structural problem it is rather than as a policy failure -
        // the same split validate/signature makes between "carries no ds:Signature" and "does not
        // cover what was required". Before, because decryption is about to destroy the evidence:
        // once the body is plaintext again, "did this arrive encrypted?" is unanswerable.
        checkRequiredReferences(ctx);

        List<Element> targets = resolveDataReferences(doc, encryptedKey);
        String dataAlgorithm = checkDataAlgorithms(targets);

        byte[] contentEncryptionKey = unwrapContentEncryptionKey(encryptedKey, dataAlgorithm);

        for (Element encryptedData : targets) {
            decryptInPlace(doc, encryptedData, contentEncryptionKey);
        }

        // The EncryptedKey is spent; leaving it would forward key material for ciphertext that is no
        // longer there, and WsSecurityInterceptor's retention rule reads this header afterwards.
        encryptedKey.getParentNode().removeChild(encryptedKey);

        checkNothingLeftEncrypted(ctx.envelope());
    }

    private void checkRequiredReferences(WsSecurityContext ctx) {
        for (EncryptionReference required : requiredReferences) {
            List<Element> elements;
            try {
                elements = resolveEncryptionReference(ctx.document(), ctx.envelope(), ctx.security(), ctx.soapNs(),
                        required, parent.getXmlConfig());
            } catch (WsSecurityXmlUtil.ReferenceResolutionException e) {
                throw new WsSecurityFaultException(FAILED_CHECK,
                        "[" + describe(required) + "] " + e.getMessage(), e);
            }
            for (Element element : elements) {
                if (!isEncrypted(element, required)) {
                    throw new WsSecurityFaultException(FAILED_CHECK,
                            "Required element (" + describe(required) + ") did not arrive encrypted.");
                }
            }
        }
    }

    /**
     * Whether {@code element} arrived encrypted in the way {@code required} demands: for
     * {@code ELEMENT}, by being an {@code xenc:EncryptedData} itself; for {@code CONTENT}, by having
     * nothing left in the clear inside it.
     * <p>
     * Every child has to be ciphertext, not merely one of them. "It contains an
     * {@code xenc:EncryptedData}" would be satisfied by a body holding one next to a plaintext
     * sibling - so a peer could encrypt one trivial element, leave the rest readable, and still pass
     * a check whose whole purpose is to make confidentiality enforceable. Non-blank character data
     * counts as in the clear for the same reason; whitespace between elements does not, since it
     * carries nothing.
     */
    private static boolean isEncrypted(Element element, EncryptionReference required) {
        if (required.getType() == EncryptionReference.Type.ELEMENT) {
            return isEncryptedData(element);
        }
        List<Element> children = childElementsOf(element);
        return !children.isEmpty()
               && children.stream().allMatch(DecryptValidatePart::isEncryptedData)
               && !hasNonBlankText(element);
    }

    private static boolean hasNonBlankText(Element element) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
                && !child.getNodeValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEncryptedData(Node node) {
        return node instanceof Element element
               && XENC_NS.equals(element.getNamespaceURI())
               && "EncryptedData".equals(element.getLocalName());
    }

    private static String describe(EncryptionReference required) {
        return required.getBy() == EncryptionReference.By.XPATH
                ? "XPATH " + required.getXpath()
                : required.getBy().toString();
    }

    /**
     * @return the single {@code xenc:EncryptedKey} child of {@code wsse:Security}
     */
    private static Element findSingleEncryptedKey(Element security) {
        List<Element> keys = getChildrenByName(security, XENC_NS, "EncryptedKey");
        if (keys.isEmpty()) {
            // Not a no-op: configuring decrypt states that this message has to be confidential, so a
            // plaintext one does not satisfy the policy. Passing it through silently would be exactly
            // the downgrade this part exists to prevent.
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "wsse:Security carries no xenc:EncryptedKey to decrypt.");
        }
        if (keys.size() > 1) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "More than one xenc:EncryptedKey element found; rejecting as ambiguous.");
        }
        return keys.getFirst();
    }

    /**
     * Checks the key transport algorithm and both digests it depends on.
     * <p>
     * The {@code ds:DigestMethod} and {@code xenc11:MGF} children matter as much as the algorithm URI
     * itself: the 1.1 {@code rsa-oaep} URI exists precisely so that the mask generation function is
     * negotiable, so a peer that sends it with {@code mgf1sha1} inside has downgraded the OAEP while
     * still naming the modern algorithm. An absent MGF child is refused for the same reason - under
     * this URI there is no default to fall back on.
     */
    private static void checkKeyTransportAlgorithm(Element encryptedKey) {
        Element method = getFirstChildByName(encryptedKey, XENC_NS, "EncryptionMethod");
        if (method == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY, "xenc:EncryptedKey has no xenc:EncryptionMethod.");
        }
        requireAllowed("xenc:EncryptionMethod", method.getAttribute("Algorithm"), ALLOWED_KEY_TRANSPORT_ALGORITHMS);
        requireAllowed("ds:DigestMethod", algorithmOf(method, DS_NS, "DigestMethod"), ALLOWED_OAEP_DIGESTS);
        requireAllowed("xenc11:MGF", algorithmOf(method, XENC11_NS, "MGF"), ALLOWED_MGF_ALGORITHMS);
    }

    /**
     * @param targets never empty - {@link #resolveDataReferences} already faults on an
     *                {@code xenc:ReferenceList} that names nothing, which is what makes the
     *                {@code iterator().next()} below safe
     * @return the data encryption algorithm every target agrees on
     */
    private static String checkDataAlgorithms(List<Element> targets) {
        Set<String> algorithms = new LinkedHashSet<>();
        for (Element encryptedData : targets) {
            Element method = getFirstChildByName(encryptedData, XENC_NS, "EncryptionMethod");
            if (method == null) {
                throw new WsSecurityFaultException(INVALID_SECURITY, "xenc:EncryptedData has no xenc:EncryptionMethod.");
            }
            String algorithm = method.getAttribute("Algorithm");
            requireAllowed("xenc:EncryptionMethod", algorithm, ALLOWED_DATA_ALGORITHMS);
            algorithms.add(algorithm);
        }
        if (algorithms.size() > 1) {
            // One EncryptedKey carries one content encryption key, whose length is fixed by the
            // algorithm; two different algorithms under it cannot both be right.
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "One xenc:EncryptedKey names xenc:EncryptedData elements with differing algorithms.");
        }
        return algorithms.iterator().next();
    }

    /**
     * @return the {@code Algorithm} of {@code parent}'s named child, or the empty string when the
     * child is absent - which no allowlist contains, so it is refused like any other unusable value
     */
    private static String algorithmOf(Element parent, String namespace, String localName) {
        Element element = getFirstChildByName(parent, namespace, localName);
        return element == null ? "" : element.getAttribute("Algorithm");
    }

    private static void requireAllowed(String what, String algorithm, Set<String> allowed) {
        if (!allowed.contains(algorithm)) {
            throw new WsSecurityFaultException(UNSUPPORTED_ALGORITHM,
                    "Unsupported " + what + " algorithm \"" + algorithm + "\".");
        }
    }

    private static List<Element> resolveDataReferences(Document doc, Element encryptedKey) {
        Element referenceList = getFirstChildByName(encryptedKey, XENC_NS, "ReferenceList");
        if (referenceList == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY, "xenc:EncryptedKey has no xenc:ReferenceList.");
        }
        List<Element> dataReferences = getChildrenByName(referenceList, XENC_NS, "DataReference");
        if (dataReferences.isEmpty()) {
            throw new WsSecurityFaultException(INVALID_SECURITY, "xenc:ReferenceList has no xenc:DataReference.");
        }

        List<Element> targets = new ArrayList<>();
        for (Element dataReference : dataReferences) {
            String uri = dataReference.getAttribute("URI");
            if (!uri.startsWith("#")) {
                throw new WsSecurityFaultException(INVALID_SECURITY,
                        "xenc:DataReference URI must be a same-document reference.");
            }
            // Ambiguity is rejected inside: a duplicated Id would let an attacker aim the decryption
            // at an element of their choosing rather than the one the reference names.
            Element target = resolveUniqueElementById(doc, uri.substring(1), INVALID_SECURITY);
            if (!isEncryptedData(target)) {
                throw new WsSecurityFaultException(INVALID_SECURITY,
                        "xenc:DataReference does not point at an xenc:EncryptedData.");
            }
            if (!(target.getParentNode() instanceof Element)) {
                throw new WsSecurityFaultException(INVALID_SECURITY,
                        "xenc:EncryptedData must not be the document element.");
            }
            targets.add(target);
        }
        return targets;
    }

    private byte[] unwrapContentEncryptionKey(Element encryptedKey, String dataAlgorithm) {
        byte[] wrapped = cipherValueOf(encryptedKey);
        try {
            byte[] contentEncryptionKey = rsaOaepCipher(Cipher.DECRYPT_MODE, privateKey).doFinal(wrapped);
            // Not optional, and easy to leave out: without it a wrong-length key reaches Cipher.init
            // and throws a distinguishable InvalidKeyException, which separates "the RSA unwrap
            // produced garbage" from "the GCM tag failed" and hands the sender an oracle.
            if (contentEncryptionKey.length != cekLengthFor(dataAlgorithm)) {
                throw new IllegalStateException("unwrapped key has the wrong length for " + dataAlgorithm);
            }
            return contentEncryptionKey;
        } catch (Exception e) {
            throw decryptionFailed(e);
        }
    }

    private void decryptInPlace(Document doc, Element encryptedData, byte[] contentEncryptionKey) {
        Element parentElement = (Element) encryptedData.getParentNode();
        boolean contentOnly = TYPE_CONTENT.equals(encryptedData.getAttribute("Type"));

        List<Node> restored;
        try {
            byte[] plaintext = decryptGcm(new SecretKeySpec(contentEncryptionKey, "AES"), cipherValueOf(encryptedData));
            // Parsing is inside the same try on purpose: "the ciphertext decrypted to something that
            // is not XML" is precisely the signal an adaptive chosen-ciphertext attack feeds on, so it
            // has to be indistinguishable from a failed authentication tag.
            restored = parsePlaintextFragment(plaintext, parentElement, doc);
        } catch (Exception e) {
            throw decryptionFailed(e);
        }

        if (contentOnly) {
            parentElement.removeChild(encryptedData);
            restored.forEach(parentElement::appendChild);
        } else {
            if (restored.size() != 1 || !(restored.getFirst() instanceof Element)) {
                throw new WsSecurityFaultException(FAILED_CHECK,
                        "An xenc:EncryptedData of Type Element must decrypt to exactly one element.");
            }
            parentElement.replaceChild(restored.getFirst(), encryptedData);
        }

        // The interceptor marked the ids of the document it received, which was before these nodes
        // existed. Without re-marking, a validate/signature listed after this part cannot dereference
        // "#id" into what was just decrypted - and that is the legitimate encrypt-then-sign case.
        restored.stream().filter(Element.class::isInstance)
                .forEach(node -> markWsuIdAttributes((Element) node));
    }

    private static byte[] cipherValueOf(Element encryptedElement) {
        Element cipherData = getFirstChildByName(encryptedElement, XENC_NS, "CipherData");
        Element cipherValue = cipherData == null ? null : getFirstChildByName(cipherData, XENC_NS, "CipherValue");
        if (cipherValue == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    encryptedElement.getNodeName() + " has no xenc:CipherData/xenc:CipherValue.");
        }
        try {
            return Base64.getDecoder().decode(cipherValue.getTextContent().replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new WsSecurityFaultException(INVALID_SECURITY, "Malformed base64 in xenc:CipherValue.");
        }
    }

    /**
     * The single exit for every cryptographic failure.
     * <p>
     * A wrong RSA-OAEP padding, a content encryption key of the wrong length, a failed GCM
     * authentication tag and plaintext that is not XML must all look identical to the sender, or the
     * fault becomes a decryption oracle. {@link WsSecurityFaultCode} already keeps the fault
     * <i>string</i> non-specific; this keeps the detail non-specific too, since a non-production
     * gateway returns that as well. The real reason goes to the log, where the operator needs it and
     * the sender cannot see it.
     */
    private static WsSecurityFaultException decryptionFailed(Exception cause) {
        log.info("XML decryption failed.", cause);
        return new WsSecurityFaultException(FAILED_CHECK, "Decryption failed.", cause);
    }

    /**
     * Refuses a message that still carries ciphertext once everything referenced has been decrypted.
     * <p>
     * The confidentiality counterpart to the signature-wrapping defence. Without it an attacker can
     * park an extra {@code xenc:EncryptedData} that no {@code xenc:DataReference} names - encrypted
     * to a key this gateway does not hold, or to nothing at all - and it flows to the backend
     * untouched, where downstream logic reads a node this gateway never inspected.
     * <p>
     * Run once at the end rather than up front, because decryption can introduce further
     * {@code xenc:EncryptedData} nodes. That also makes super-encryption a fault rather than a
     * recursion, which bounds the work an attacker can ask for; nested encryption is not supported.
     */
    private static void checkNothingLeftEncrypted(Element envelope) {
        forEachDescendantElement(envelope, element -> {
            if (isEncryptedData(element)) {
                throw new WsSecurityFaultException(INVALID_SECURITY,
                        "The message still carries an xenc:EncryptedData that no xenc:DataReference named.");
            }
        });
    }

    public List<EncryptionReference> getRequiredReferences() {
        return requiredReferences;
    }

    /**
     * @description The elements that must have arrived encrypted. Validation fails if any of them was
     * sent in the clear. Without this list, a message carrying no encryption at all is accepted, so
     * this is what makes confidentiality an enforced requirement rather than an option the peer may
     * decline.
     */
    @MCChildElement(order = 1)
    public void setRequiredReferences(List<EncryptionReference> requiredReferences) {
        this.requiredReferences = requiredReferences == null ? List.of() : List.copyOf(requiredReferences);
    }
}
