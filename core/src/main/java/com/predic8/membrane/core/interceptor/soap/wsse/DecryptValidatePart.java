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
import java.security.SecureRandom;
import java.util.*;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.*;

/**
 * @description Decrypts the XML Encryption content of the inbound message: the
 * <code>xenc:EncryptedKey</code> in the <code>wsse:Security</code> header is opened with the
 * enclosing <code>wsSecurity</code> element's <code>keystore</code> private key, and every
 * <code>xenc:EncryptedData</code> it names is replaced by its plaintext.
 * <p>By default the accepted algorithms are narrower than what <code>encrypt</code> can be configured
 * to produce: AES-128/256-GCM for content and RSA-OAEP with SHA-256 and MGF1-SHA-256 for the key. A
 * message using anything else — an AES-CBC downgrade, RSA-1.5, or the modern OAEP URI carrying an
 * SHA-1 mask generation function — is answered with <code>wsse:UnsupportedAlgorithm</code> before any
 * cipher is constructed. That asymmetry is deliberate: what a peer may send is a separate decision
 * from what this gateway emits, because accepting a padding-oracle-shaped algorithm exposes this
 * gateway rather than the peer. <code>allowedLegacyAlgorithms</code> adds individual AES-CBC or RSA-1.5 exceptions
 * for a peer that supports nothing else; the OAEP digest and mask generation function stay fixed
 * either way.</p>
 * <p>One message, one recipient: a <code>wsse:Security</code> header carrying more than one
 * <code>xenc:EncryptedKey</code> is refused rather than searched for the key this gateway can open.
 * A multi-recipient message is a sender that addressed no header at any <code>actor</code>, and the
 * fix for it is to target each header at the role meant to process it.</p>
 * <p>A message carrying no <code>xenc:EncryptedKey</code> is refused outright, so configuring this
 * element already makes encryption mandatory. What <code>requiredReferences</code> adds is
 * <i>which</i> elements had to arrive encrypted: without it, a peer that encrypted one trivial
 * element and sent the rest of the message readable passes. List the elements that must have
 * arrived encrypted to make confidentiality enforceable. See
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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * The default: only the authenticated GCM modes. XML Encryption's CBC modes carry no
     * authentication tag, which is exactly what makes a decrypting gateway usable as the oracle in
     * the Jager-Somorovsky backwards-compatibility attack - so they are accepted only where an
     * operator has listed the algorithm in {@link #allowedLegacyAlgorithms}, never merely because this gateway can also
     * emit them.
     */
    private static final Set<String> ALLOWED_DATA_ALGORITHMS = Set.of(AES128_GCM, AES256_GCM);
    /** Likewise: RSA-1.5 is Bleichenbacher's oracle, so it is not in the default set. */
    private static final Set<String> ALLOWED_KEY_TRANSPORT_ALGORITHMS = Set.of(RSA_OAEP);
    /**
     * The OAEP digest and mask generation function, which stay fixed <i>whatever</i>
     * {@link #allowedLegacyAlgorithms} contains - folding these two into the legacy exceptions is the obvious
     * simplification and the wrong one.
     * <p>
     * They are a different question from "may this peer use an old algorithm". A peer that sends
     * {@code xenc11#rsa-oaep} has claimed the modern algorithm, and the whole reason that URI makes
     * the mask generation function negotiable is that the MGF is then stated separately - so
     * accepting {@code mgf1sha1} inside it is not backwards compatibility, it is a downgrade of the
     * algorithm the peer itself named. A peer that genuinely cannot do OAEP asks for
     * {@link XmlEncryptionUtil#RSA_1_5}, where these two elements do not appear at all.
     */
    private static final Set<String> ALLOWED_OAEP_DIGESTS = Set.of(SHA256_DIGEST);
    private static final Set<String> ALLOWED_MGF_ALGORITHMS = Set.of(MGF1_SHA256);

    /** The supported legacy exceptions. */
    private static final Set<String> LEGACY_DATA_ALGORITHMS = Set.of(AES128_CBC, AES192_CBC, AES256_CBC);
    private static final Set<String> LEGACY_KEY_TRANSPORT_ALGORITHMS = Set.of(RSA_1_5);

    private List<EncryptionReference> requiredReferences = new ArrayList<>();
    private List<LegacyEncryptionAlgorithm> allowedLegacyAlgorithms = List.of();

    private PrivateKey privateKey;
    // Resolved in init() from allowedLegacyAlgorithms and read-only afterwards, like every other field
    // here: one instance serves every request thread.
    private Set<String> allowedDataAlgorithms;
    private Set<String> allowedKeyTransportAlgorithms;

    @Override
    protected void init() {
        if (parent.getKeyStore() == null) {
            throw new ConfigurationException(
                    "wsSecurity validate/decrypt requires a <keystore> on the enclosing wsSecurity element.");
        }
        requiredReferences.forEach(EncryptionReference::validate);
        resolveAllowedAlgorithms();
        loadDecryptionKey();
    }

    /**
     * Fixes what this part accepts, once, and says so if it is more than the default.
     * <p>
     * The warning is startup-only and deliberately not repeated per message: the algorithm of an
     * inbound message is the <i>peer's</i> choice, so a per-message warning would be a log-flood
     * vector for anyone who can reach this endpoint. A refusal does log per message, at
     * {@code info} - see {@link #requireAllowed}.
     */
    private void resolveAllowedAlgorithms() {
        Set<String> data = new HashSet<>(ALLOWED_DATA_ALGORITHMS);
        Set<String> keyTransport = new HashSet<>(ALLOWED_KEY_TRANSPORT_ALGORITHMS);
        for (LegacyEncryptionAlgorithm algorithm : new LinkedHashSet<>(allowedLegacyAlgorithms)) {
            if (LEGACY_DATA_ALGORITHMS.contains(algorithm.getUri())) {
                data.add(algorithm.getUri());
            } else {
                keyTransport.add(algorithm.getUri());
            }
            log.warn("wsSecurity validate/decrypt accepts legacy algorithm {} ({}). Enable this only for a peer " +
                     "that cannot be upgraded: accepting legacy encryption exposes this gateway to padding-oracle attacks.",
                    algorithm, algorithm.getUri());
        }
        allowedDataAlgorithms = Set.copyOf(data);
        allowedKeyTransportAlgorithms = Set.copyOf(keyTransport);
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
        String keyTransportAlgorithm = checkKeyTransportAlgorithm(encryptedKey);

        // After the structural checks, before any decryption. After, so that "there is nothing here
        // to decrypt" is reported as the structural problem it is rather than as a policy failure -
        // the same split validate/signature makes between "carries no ds:Signature" and "does not
        // cover what was required". Before, because decryption is about to destroy the evidence:
        // once the body is plaintext again, "did this content arrive encrypted?" is unanswerable.
        checkRequiredContentReferences(ctx);

        List<Element> targets = resolveDataReferences(doc, encryptedKey);
        String dataAlgorithm = checkDataAlgorithms(targets);

        byte[] contentEncryptionKey = unwrapContentEncryptionKey(encryptedKey, keyTransportAlgorithm, dataAlgorithm);

        // Compared by identity: two distinct elements of the same name are not the same target, and
        // DOM nodes have no value equality that would say otherwise.
        Set<Element> restoredElements = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Element encryptedData : targets) {
            Element restored = decryptInPlace(doc, encryptedData, dataAlgorithm, contentEncryptionKey);
            if (restored != null) {
                restoredElements.add(restored);
            }
        }

        // The EncryptedKey is spent; leaving it would forward key material for ciphertext that is no
        // longer there, and WsSecurityInterceptor's retention rule reads this header afterwards.
        encryptedKey.getParentNode().removeChild(encryptedKey);

        checkNothingLeftEncrypted(ctx.envelope(),
                getFirstChildByName(ctx.envelope(), ctx.soapNs(), "Header"), security);
        checkRequiredElementReferences(ctx, restoredElements);
    }

    /**
     * The {@code CONTENT} requirements, checked before anything is decrypted: the element itself is
     * still where the reference names it, and everything inside it has to be ciphertext.
     */
    private void checkRequiredContentReferences(WsSecurityContext ctx) {
        for (EncryptionReference required : requiredReferences) {
            if (required.getType() != EncryptionReference.Type.CONTENT) {
                continue;
            }
            for (Element element : resolveRequired(ctx, required)) {
                if (!isFullyEncryptedContent(element)) {
                    throw new WsSecurityFaultException(FAILED_CHECK,
                            "Required element (" + required.describe() + ") did not arrive encrypted.");
                }
            }
        }
    }

    /**
     * The {@code ELEMENT} requirements, checked after decryption instead.
     * <p>
     * They have to be: element encryption <i>replaces</i> the target with an
     * {@code xenc:EncryptedData}, so while the message is still encrypted there is no
     * {@code wsse:UsernameToken} and no XPath match left for the reference to resolve to - the
     * element exists again only once it has been decrypted. Checking these up front would therefore
     * reject exactly the messages that satisfy the requirement.
     * <p>
     * The evidence decryption would otherwise destroy is carried forward explicitly instead:
     * {@code restoredElements} holds the elements an {@code ELEMENT} decryption produced, and
     * nothing else, so a target the peer sent in the clear is not in it and a target that arrived
     * inside someone else's {@code CONTENT} ciphertext is not either.
     */
    private void checkRequiredElementReferences(WsSecurityContext ctx, Set<Element> restoredElements) {
        for (EncryptionReference required : requiredReferences) {
            if (required.getType() != EncryptionReference.Type.ELEMENT) {
                continue;
            }
            for (Element element : resolveRequired(ctx, required)) {
                if (!restoredElements.contains(element)) {
                    throw new WsSecurityFaultException(FAILED_CHECK,
                            "Required element (" + required.describe() + ") did not arrive encrypted.");
                }
            }
        }
    }

    private List<Element> resolveRequired(WsSecurityContext ctx, EncryptionReference required) {
        try {
            return resolveEncryptionReference(ctx.document(), ctx.envelope(), ctx.security(), ctx.soapNs(),
                    required, parent.getXmlConfig());
        } catch (WsSecurityXmlUtil.ReferenceResolutionException e) {
            throw new WsSecurityFaultException(FAILED_CHECK, "[" + required.describe() + "] " + e.getMessage(), e);
        }
    }

    /**
     * Whether {@code element} arrived with nothing left in the clear inside it, which is what a
     * {@code CONTENT} requirement demands.
     * <p>
     * Every child has to be ciphertext, not merely one of them. "It contains an
     * {@code xenc:EncryptedData}" would be satisfied by a body holding one next to a plaintext
     * sibling - so a peer could encrypt one trivial element, leave the rest readable, and still pass
     * a check whose whole purpose is to make confidentiality enforceable. Non-blank character data
     * counts as in the clear for the same reason; whitespace between elements does not, since it
     * carries nothing.
     */
    private static boolean isFullyEncryptedContent(Element element) {
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
     * Checks the key transport algorithm and, under OAEP, both digests it depends on.
     * <p>
     * The {@code ds:DigestMethod} and {@code xenc11:MGF} children matter as much as the algorithm URI
     * itself: the 1.1 {@code rsa-oaep} URI exists precisely so that the mask generation function is
     * negotiable, so a peer that sends it with {@code mgf1sha1} inside has downgraded the OAEP while
     * still naming the modern algorithm. An absent MGF child is refused for the same reason - under
     * this URI there is no default to fall back on.
     * <p>
     * Under {@code rsa-1_5} neither child is checked, because neither means anything there: PKCS#1
     * v1.5 padding has no digest and no mask generation function. They are not refused if present
     * either - a peer that emits a stray one has written something inert, not a downgrade.
     *
     * @return the key transport algorithm, which decides both the unwrapping cipher and how a failed
     * unwrap has to be handled
     */
    private String checkKeyTransportAlgorithm(Element encryptedKey) {
        Element method = getFirstChildByName(encryptedKey, XENC_NS, "EncryptionMethod");
        if (method == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY, "xenc:EncryptedKey has no xenc:EncryptionMethod.");
        }
        String algorithm = method.getAttribute("Algorithm");
        requireAllowed("xenc:EncryptionMethod", algorithm, allowedKeyTransportAlgorithms);
        if (!RSA_1_5.equals(algorithm)) {
            requireAllowed("ds:DigestMethod", algorithmOf(method, DS_NS, "DigestMethod"), ALLOWED_OAEP_DIGESTS);
            requireAllowed("xenc11:MGF", algorithmOf(method, XENC11_NS, "MGF"), ALLOWED_MGF_ALGORITHMS);
        }
        return algorithm;
    }

    /**
     * @param targets never empty - {@link #resolveDataReferences} already faults on an
     *                {@code xenc:ReferenceList} that names nothing, which is what makes the
     *                {@code iterator().next()} below safe
     * @return the data encryption algorithm every target agrees on
     */
    private String checkDataAlgorithms(List<Element> targets) {
        Set<String> algorithms = new LinkedHashSet<>();
        for (Element encryptedData : targets) {
            Element method = getFirstChildByName(encryptedData, XENC_NS, "EncryptionMethod");
            if (method == null) {
                throw new WsSecurityFaultException(INVALID_SECURITY, "xenc:EncryptedData has no xenc:EncryptionMethod.");
            }
            String algorithm = method.getAttribute("Algorithm");
            requireAllowed("xenc:EncryptionMethod", algorithm, allowedDataAlgorithms);
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

    /**
     * The refusal is logged as well as faulted, because the fault cannot say what was wrong: WS-
     * Security fault text here is kept non-specific so it cannot serve as an oracle, which leaves the
     * log as the only place an operator can find out that a peer is being turned away over an
     * algorithm. {@code info} rather than {@code warn} because the trigger is peer-controlled, like
     * every other detection in this package.
     * <p>
     * The hint is attached only when {@code allowedLegacyAlgorithms} would actually have accepted this
     * value. Naming the list for a refusal it does not affect - an SHA-1 mask generation function,
     * most obviously - would send an operator to enable it and then puzzle over the same fault.
     */
    private static void requireAllowed(String what, String algorithm, Set<String> allowed) {
        if (!allowed.contains(algorithm)) {
            if (LEGACY_DATA_ALGORITHMS.contains(algorithm) || LEGACY_KEY_TRANSPORT_ALGORITHMS.contains(algorithm)) {
                log.info("Refused inbound {} algorithm \"{}\": it is one of the legacy algorithms, which are not " +
                         "accepted by default. If this peer cannot be upgraded, add the algorithm to allowedLegacyAlgorithms " +
                         "on wsSecurity validate/decrypt - and read what that costs before you do.", what, algorithm);
            } else {
                log.info("Refused inbound {} algorithm \"{}\"; accepted: {}.", what, algorithm, allowed);
            }
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
        Set<String> seenIds = new HashSet<>();
        for (Element dataReference : dataReferences) {
            String uri = dataReference.getAttribute("URI");
            if (!uri.startsWith("#")) {
                throw new WsSecurityFaultException(INVALID_SECURITY,
                        "xenc:DataReference URI must be a same-document reference.");
            }
            // Naming the same EncryptedData twice is refused rather than deduplicated: the second
            // pass would find a node this loop's own decryption had already detached from the
            // document, and there is no message a peer could mean by it.
            if (!seenIds.add(uri.substring(1))) {
                throw new WsSecurityFaultException(INVALID_SECURITY,
                        "xenc:ReferenceList names \"" + uri + "\" more than once.");
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

    /**
     * Opens the {@code xenc:EncryptedKey} with this gateway's private key.
     * <p>
     * Under RSA-1.5 a failure does not throw. Bleichenbacher's attack is built entirely on telling
     * "the PKCS#1 padding was well-formed" apart from "it was not", so a well-formedness failure -
     * or an unwrapped key of the wrong length, which is the same information - is answered with a
     * random key of the length the data algorithm requires, and decryption carries on with it. The
     * content decryption then fails as it would for any wrong key, which is the only thing the
     * sender gets to observe. This is the countermeasure RFC 3218 describes and XML Encryption 1.1
     * requires of an implementation that accepts the algorithm at all.
     * <p>
     * OAEP keeps the plain throw: it is not a Bleichenbacher oracle, and every failure on that path
     * already funnels into the one indistinguishable fault.
     */
    private byte[] unwrapContentEncryptionKey(Element encryptedKey, String keyTransportAlgorithm,
                                              String dataAlgorithm) {
        byte[] wrapped = cipherValueOf(encryptedKey);
        int expectedLength = cekLengthFor(dataAlgorithm);
        try {
            byte[] contentEncryptionKey =
                    keyTransportCipher(keyTransportAlgorithm, Cipher.DECRYPT_MODE, privateKey).doFinal(wrapped);
            // Not optional, and easy to leave out: without it a wrong-length key reaches Cipher.init
            // and throws a distinguishable InvalidKeyException, which separates "the RSA unwrap
            // produced garbage" from "the GCM tag failed" and hands the sender an oracle.
            if (contentEncryptionKey.length != expectedLength) {
                throw new IllegalStateException("unwrapped key has the wrong length for " + dataAlgorithm);
            }
            return contentEncryptionKey;
        } catch (Exception e) {
            if (RSA_1_5.equals(keyTransportAlgorithm)) {
                log.info("RSA-1.5 key unwrap failed; continuing with a random content encryption key so that the " +
                         "failure is indistinguishable from a content decryption failure.", e);
                // Length taken from the data algorithm, never from the blob that just failed to
                // unwrap - a length derived from the failure would reinstate the distinction this
                // substitution exists to remove.
                return randomContentEncryptionKey(expectedLength);
            }
            throw decryptionFailed(e);
        }
    }

    private static byte[] randomContentEncryptionKey(int length) {
        byte[] key = new byte[length];
        // The shared instance, not a fresh one: this runs on a path a peer can trigger at will, and
        // constructing a SecureRandom per failed unwrap would repeat provider lookup and seeding for
        // each of them.
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    /**
     * @return the element an {@code ELEMENT} decryption restored, or {@code null} for a
     * {@code CONTENT} one, so that {@link #checkRequiredElementReferences} can tell an element that
     * arrived as its own {@code xenc:EncryptedData} from one that was there all along
     */
    private Element decryptInPlace(Document doc, Element encryptedData, String dataAlgorithm,
                                   byte[] contentEncryptionKey) {
        Element parentElement = (Element) encryptedData.getParentNode();
        boolean contentOnly = TYPE_CONTENT.equals(encryptedData.getAttribute("Type"));

        List<Node> restored;
        try {
            byte[] plaintext = decryptData(dataAlgorithm, new SecretKeySpec(contentEncryptionKey, "AES"),
                    cipherValueOf(encryptedData));
            // Parsing is inside the same try on purpose: "the ciphertext decrypted to something that
            // is not XML" is precisely the signal an adaptive chosen-ciphertext attack feeds on, so it
            // has to be indistinguishable from a failed authentication tag - or, under CBC, from a
            // failed padding check, which is the whole of the Jager-Somorovsky attack.
            restored = parsePlaintextFragment(plaintext, parentElement, doc);
        } catch (Exception e) {
            throw decryptionFailed(e);
        }

        Element restoredElement = null;
        if (contentOnly) {
            // Put back where the ciphertext stood rather than appended: a parent carrying anything
            // besides the xenc:EncryptedData would otherwise see the plaintext move behind it, and a
            // validate/signature listed after this part then canonicalizes a different node order
            // than the sender signed.
            restored.forEach(node -> parentElement.insertBefore(node, encryptedData));
            parentElement.removeChild(encryptedData);
        } else {
            if (restored.size() != 1 || !(restored.getFirst() instanceof Element element)) {
                throw new WsSecurityFaultException(FAILED_CHECK,
                        "An xenc:EncryptedData of Type Element must decrypt to exactly one element.");
            }
            parentElement.replaceChild(element, encryptedData);
            restoredElement = element;
        }

        // The interceptor marked the ids of the document it received, which was before these nodes
        // existed. Without re-marking, a validate/signature listed after this part cannot dereference
        // "#id" into what was just decrypted - and that is the legitimate encrypt-then-sign case.
        restored.stream().filter(Element.class::isInstance)
                .forEach(node -> markWsuIdAttributes((Element) node));
        return restoredElement;
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
    private static void checkNothingLeftEncrypted(Element element, Element header, Element security) {
        // findSecurity already selected the unique header for this actor. Other direct Security
        // header blocks belong to other recipients, whose ciphertext must pass through untouched.
        // Restrict this exception to header blocks: a nested Security element in the body must not
        // hide unreferenced ciphertext from this check.
        if (element.getParentNode() == header && element != security
            && WSSE_NS.equals(element.getNamespaceURI()) && "Security".equals(element.getLocalName())) {
            return;
        }
        if (isEncryptedData(element)) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "The message still carries an xenc:EncryptedData that no xenc:DataReference named.");
        }
        for (Element child : childElementsOf(element)) {
            checkNothingLeftEncrypted(child, header, security);
        }
    }

    public List<EncryptionReference> getRequiredReferences() {
        return requiredReferences;
    }

    /**
     * @description The elements that must have arrived encrypted. Validation fails if any of them was
     * sent in the clear. <code>decrypt</code> already requires every message to carry an
     * <code>xenc:EncryptedKey</code>, whether or not this list is set; what it adds is <i>which</i>
     * elements had to be among the ones that key decrypts — without it, a peer that encrypted one
     * trivial element and sent the rest in the clear still passes. Each entry's <code>type</code>
     * says <i>how</i> the element had to arrive, and has to match what the sender did:
     * <code>CONTENT</code> requires everything inside the element to be ciphertext,
     * <code>ELEMENT</code> requires the element itself to have been replaced by an
     * <code>xenc:EncryptedData</code>.
     */
    @MCChildElement(order = 1)
    public void setRequiredReferences(List<EncryptionReference> requiredReferences) {
        this.requiredReferences = requiredReferences == null ? List.of() : List.copyOf(requiredReferences);
    }

    public List<LegacyEncryptionAlgorithm> getAllowedLegacyAlgorithms() {
        return allowedLegacyAlgorithms;
    }

    /**
     * @description Legacy algorithms additionally accepted on inbound messages. Each listed algorithm
     * is enabled independently and logs a warning at startup. An omitted or empty list preserves the
     * modern defaults. Enable an exception only for a peer that supports nothing else.
     * <p>AES-CBC and RSA-1.5 expose the decrypting gateway to padding-oracle attacks. These exceptions
     * are independent of outbound encryption settings and do not relax OAEP digest, MGF, or signature checks.</p>
     * @example [aes128_cbc]
     */
    @MCChildElement(order = 2)
    public void setAllowedLegacyAlgorithms(List<LegacyEncryptionAlgorithm> allowedLegacyAlgorithms) {
        this.allowedLegacyAlgorithms = allowedLegacyAlgorithms == null ? List.of() : List.copyOf(allowedLegacyAlgorithms);
    }
}
