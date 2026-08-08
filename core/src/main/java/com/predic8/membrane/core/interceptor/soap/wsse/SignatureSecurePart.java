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
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.security.KeyStoreUtil;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.ConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.ExcC14NParameterSpec;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.INVALID_SECURITY;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;

/**
 * @description Adds an XML Signature (<a href="http://www.w3.org/2000/09/xmldsig#">XML-DSig</a>) to
 * the <code>wsse:Security</code> header, signing the elements listed in <code>references</code>.
 * Follows the WS-Security convention used by Apache CXF: each signed element receives a
 * <code>wsu:Id</code> that a detached <code>ds:Reference</code> points at. Signs with the enclosing
 * <code>wsSecurity</code> element's <code>keystore</code>.
 */
@MCElement(name = "signature", component = false, id = "wsSecurity-signature")
public class SignatureSecurePart extends SecurePart {

    private static final String DEFAULT_SIGNATURE_ALGORITHM = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    private static final String DEFAULT_DIGEST_ALGORITHM = "http://www.w3.org/2001/04/xmlenc#sha256";
    private static final String DEFAULT_CANONICALIZATION_ALGORITHM = CanonicalizationMethod.EXCLUSIVE;

    private List<SignatureReference> references = new ArrayList<>();
    private String signatureAlgorithm = DEFAULT_SIGNATURE_ALGORITHM;
    private String digestAlgorithm = DEFAULT_DIGEST_ALGORITHM;
    private String canonicalizationAlgorithm = DEFAULT_CANONICALIZATION_ALGORITHM;
    private X509DataKeyInfo x509Data;
    private SecurityTokenReferenceKeyInfo securityTokenReference;
    private KeyIdentifierKeyInfo keyIdentifier;

    private PrivateKey privateKey;
    private X509Certificate certificate;

    private static final List<String> SUPPORTED_DIGEST_ALGORITHMS = List.of(
            DigestMethod.SHA1, DigestMethod.SHA224, DigestMethod.SHA256, DigestMethod.SHA384,
            DigestMethod.SHA512, DigestMethod.RIPEMD160, DigestMethod.SHA3_224, DigestMethod.SHA3_256,
            DigestMethod.SHA3_384, DigestMethod.SHA3_512);
    // HMAC is deliberately absent: signing here always uses the keystore's PrivateKey, which a MAC
    // algorithm cannot accept, so advertising one would only move the failure from init() to the
    // first message. RSA-SHA1 stays for legacy backends that require it - note that validate/signature
    // refuses SHA-1 on the way in, by design.
    private static final List<String> SUPPORTED_SIGNATURE_ALGORITHMS = List.of(
            SignatureMethod.RSA_SHA1,
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384",
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512",
            "http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1",
            "http://www.w3.org/2007/05/xmldsig-more#sha384-rsa-MGF1",
            "http://www.w3.org/2007/05/xmldsig-more#sha512-rsa-MGF1",
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256",
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384",
            "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512");
    private static final List<String> EXCLUSIVE_CANONICALIZATION_ALGORITHMS = List.of(
            CanonicalizationMethod.EXCLUSIVE, CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS);

    private static final List<String> SUPPORTED_CANONICALIZATION_ALGORITHMS = List.of(
            CanonicalizationMethod.INCLUSIVE, CanonicalizationMethod.EXCLUSIVE,
            CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS, CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS);

    @Override
    protected void init() {
        validateConfiguration();
        loadSigningMaterial();
    }

    /**
     * Whether this signature covers what {@code by} names, which the enclosing element needs to know to
     * check that the part creating it is listed before this one.
     */
    boolean references(SignatureReference.By by) {
        return references.stream().anyMatch(r -> r.getBy() == by);
    }

    private void validateConfiguration() {
        if (parent.getKeyStore() == null) {
            throw new ConfigurationException("wsSecurity secure/signature requires a <keystore> on the enclosing wsSecurity element.");
        }
        if (references.isEmpty()) {
            throw new ConfigurationException("wsSecurity secure/signature requires at least one <reference> child element.");
        }
        if (Stream.of(x509Data, securityTokenReference, keyIdentifier).filter(Objects::nonNull).count() > 1) {
            throw new ConfigurationException(
                    "wsSecurity secure/signature accepts at most one of <x509Data>, <securityTokenReference>, or <keyIdentifier>.");
        }
        if (securityTokenReference == null && references.stream().anyMatch(r -> r.getBy() == SignatureReference.By.BST)) {
            throw new ConfigurationException("reference by: BST requires a <securityTokenReference> KeyInfo mode.");
        }
        references.forEach(SignatureReference::validate);
        validateAlgorithms();
    }

    /**
     * The lists are the authority, not the {@code XMLSignatureFactory}. Asking the factory whether it
     * knows an algorithm accepts more than this part can actually use - a MAC algorithm, most
     * obviously, which it constructs happily and then cannot sign a message with - so a configuration
     * would pass {@code init()} and fail on the first message instead.
     */
    private void validateAlgorithms() {
        requireSupported("digestAlgorithm", digestAlgorithm, SUPPORTED_DIGEST_ALGORITHMS);
        requireSupported("signatureAlgorithm", signatureAlgorithm, SUPPORTED_SIGNATURE_ALGORITHMS);
        requireSupported("canonicalizationAlgorithm", canonicalizationAlgorithm, SUPPORTED_CANONICALIZATION_ALGORITHMS);
    }

    private static void requireSupported(String attribute, String value, List<String> supported) {
        if (!supported.contains(value)) {
            throw new ConfigurationException("Unsupported " + attribute + " \"" + value +
                    "\" on wsSecurity secure/signature. Supported values: " + String.join(", ", supported));
        }
    }

    private void loadSigningMaterial() {
        KeyStore keyStore = parent.getKeyStore();
        try {
            char[] keyPassword = resolveKeyPassword(keyStore);
            java.security.KeyStore ks = StaticSSLContext.openKeyStore(
                    keyStore, keyPassword, parent.getRouter().getResolverMap(), parent.beanBaseLocation());

            String alias = keyStore.getKeyAlias() != null
                    ? KeyStoreUtil.aliasOrThrow(ks, keyStore.getKeyAlias())
                    : KeyStoreUtil.firstAliasOrThrow(ks);

            privateKey = (PrivateKey) ks.getKey(alias, keyPassword);
            certificate = (X509Certificate) ks.getCertificate(alias);
            if (privateKey == null) {
                throw new ConfigurationException("Keystore alias \"" + alias + "\" holds no private key.");
            }
            if (certificate == null) {
                throw new ConfigurationException("Keystore alias \"" + alias + "\" holds no X.509 certificate.");
            }
        } catch (Exception e) {
            throw new ConfigurationException("Could not load signing key from the wsSecurity keystore.", e);
        }
    }

    // For a PKCS12 keystore, the key is commonly protected by the same password as the store
    // itself; fall back to it when no distinct keyPassword is configured.
    private static char[] resolveKeyPassword(KeyStore keyStore) {
        if (keyStore.getKeyPassword() != null) {
            return keyStore.getKeyPassword().toCharArray();
        }
        if (keyStore.getPassword() != null) {
            return keyStore.getPassword().toCharArray();
        }
        return "changeit".toCharArray();
    }

    @Override
    void process(WsSecurityContext ctx) throws Exception {
        Document doc = ctx.document();
        Element security = ctx.security();

        if (securityTokenReference != null
            && references.stream().anyMatch(r -> r.getBy() == SignatureReference.By.BST)
            && getFirstChildByName(security, WSSE_NS, "BinarySecurityToken") == null) {
            // Created here, before reference resolution, so a "by: BST" reference can pick it up and
            // cover it with a ds:Reference; appendSecurityTokenReferenceKeyInfo() then reuses this
            // same element instead of creating its own.
            //
            // No token the peer sent can be in the way: the enclosing element removes the inbound
            // wsse:Security header it owns before any secure part runs. An existing one is therefore
            // this gateway's own, put there by an earlier signature part in the same list, and holds
            // the same certificate - so it is reused rather than duplicated.
            security.appendChild(createBinarySecurityToken(doc));
        }

        List<String> referencedIds = new ArrayList<>();
        try {
            for (SignatureReference reference : references) {
                List<Element> elements = resolveReference(doc, ctx.envelope(), security, ctx.soapNs(), reference,
                        parent.getXmlConfig());
                if (reference.getId() != null && elements.size() > 1) {
                    throw new WsSecurityXmlUtil.ReferenceResolutionException(
                            "reference \"" + reference.getId() + "\" has an explicit id but its xpath matched " +
                            elements.size() + " elements; an explicit id can only be used when exactly one element matches.");
                }
                for (Element element : elements) {
                    referencedIds.add(ensureId(element, reference));
                }
            }
        } catch (WsSecurityXmlUtil.ReferenceResolutionException e) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "Could not resolve signature reference: " + e.getMessage(), e);
        }

        sign(doc, ctx.envelope(), security, referencedIds);
    }

    /**
     * The id the {@code ds:Reference} for {@code target} points at, put on the element if it does not
     * already carry one.
     * <p>
     * An id the element already has is reused as it stands, including an unqualified {@code Id}: adding
     * a {@code wsu:Id} of the same value next to it would leave the element with two ID attributes
     * holding one value, which some consumers read as an ID collision.
     */
    private static String ensureId(Element target, SignatureReference reference) {
        if (reference.getId() == null) {
            String existing = idOf(target);
            if (!existing.isEmpty()) {
                // Re-marked rather than assumed: the interceptor marks the ids of the document it
                // received, but an element created by an earlier secure part in this same list - a
                // wsse:BinarySecurityToken, say - came after that, and "#id" would not dereference.
                markIdAttribute(target);
                return existing;
            }
        }
        String id = reference.getId() != null ? reference.getId() : "sig-" + UUID.randomUUID();
        declareWsuId(target, id);
        target.setIdAttributeNS(WSU_NS, "Id", true);
        return id;
    }

    private void sign(Document doc, Element envelope, Element security, List<String> referencedIds) throws Exception {
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        C14NMethodParameterSpec signedInfoC14nSpec = signedInfoC14nSpec(envelope);

        DigestMethod digestMethod = fac.newDigestMethod(digestAlgorithm, null);
        Transform transform = fac.newTransform(canonicalizationAlgorithm, (C14NMethodParameterSpec) null);

        List<Reference> refs = new ArrayList<>();
        for (String id : referencedIds) {
            refs.add(fac.newReference("#" + id, digestMethod, List.of(transform), null, null));
        }

        SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(canonicalizationAlgorithm, signedInfoC14nSpec),
                fac.newSignatureMethod(signatureAlgorithm, null),
                refs);

        DOMSignContext dsc = new DOMSignContext(privateKey, security);
        dsc.putNamespacePrefix(XMLSignature.XMLNS, "ds");

        String signatureId = "SIG-" + UUID.randomUUID();

        if (securityTokenReference != null || keyIdentifier != null) {
            // ds:KeyInfo can't hold arbitrary wsse: content via the KeyInfoFactory API, so sign
            // without one, then build and append the wsse:SecurityTokenReference-based KeyInfo by
            // hand. KeyInfo is not itself covered by the signature (no ds:Reference points at it),
            // so appending it afterwards doesn't affect what was just signed.
            fac.newXMLSignature(signedInfo, null, List.of(), signatureId, null).sign(dsc);
            Element signatureElement = (Element) security.getLastChild();
            removeWhitespaceFromSignatureValue(signatureElement);
            if (keyIdentifier != null) {
                appendKeyIdentifierKeyInfo(doc, signatureElement);
            } else {
                appendSecurityTokenReferenceKeyInfo(doc, security, signatureElement);
            }
        } else {
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            X509Data x509DataStructure = kif.newX509Data(List.of(certificate));
            KeyInfo keyInfo = kif.newKeyInfo(List.of(x509DataStructure));
            fac.newXMLSignature(signedInfo, keyInfo, List.of(), signatureId, null).sign(dsc);
            removeWhitespaceFromSignatureValue((Element) security.getLastChild());
        }
    }

    /**
     * The parameters for {@code ds:SignedInfo}'s own canonicalization.
     * <p>
     * For exclusive c14n that is the ancestor SOAP envelope prefix (the WSS4J/CXF convention),
     * which guards SignedInfo's canonical form against the prefix being rewritten in transit. The
     * per-reference {@code Transform} in {@code sign} deliberately gets none, i.e. pure exclusive
     * c14n for just the referenced element.
     * <p>
     * Inclusive c14n takes no {@code ExcC14NParameterSpec} at all - it has no prefix list, and
     * passing one is rejected by the factory. Since {@code INCLUSIVE} and
     * {@code INCLUSIVE_WITH_COMMENTS} are both advertised as supported, an unconditional spec would
     * make a configuration that passes {@code validateAlgorithms} fail at signing time instead.
     */
    private C14NMethodParameterSpec signedInfoC14nSpec(Element envelope) {
        if (!EXCLUSIVE_CANONICALIZATION_ALGORITHMS.contains(canonicalizationAlgorithm)) {
            return null;
        }
        String soapPrefix = envelope.getPrefix();
        // An empty prefix list is what exclusive c14n does by default, so there is nothing to say.
        return soapPrefix == null || soapPrefix.isEmpty() ? null : new ExcC14NParameterSpec(List.of(soapPrefix));
    }

    /**
     * The JDK's built-in JSR 105 {@code XMLSignatureFactory} (backed by Apache Santuario) wraps
     * the Base64-encoded signature bytes it writes into {@code ds:SignatureValue} at a fixed line
     * length (historically 76 characters), inserting newlines into the element's text content.
     * That whitespace is harmless for signature validity - Base64 decoding ignores it - but some
     * WS-Security consumers expect the value on a single line, so it is stripped here for
     * compatibility.
     */
    private static void removeWhitespaceFromSignatureValue(Element signatureElement) {
        Element signatureValue = (Element) signatureElement
                .getElementsByTagNameNS(XMLSignature.XMLNS, "SignatureValue").item(0);
        signatureValue.setTextContent(signatureValue.getTextContent().replaceAll("\\s+", ""));
    }

    private void appendSecurityTokenReferenceKeyInfo(Document doc, Element security, Element signatureElement) throws Exception {
        Element binarySecurityToken = getFirstChildByName(security, WSSE_NS, "BinarySecurityToken");
        if (binarySecurityToken == null) {
            binarySecurityToken = createBinarySecurityToken(doc);
            security.insertBefore(binarySecurityToken, signatureElement);
        }
        String tokenId = binarySecurityToken.getAttributeNS(WSU_NS, "Id");

        Element reference = doc.createElementNS(WSSE_NS, "wsse:Reference");
        reference.setAttribute("URI", "#" + tokenId);
        reference.setAttribute("ValueType", X509_V3_VALUE_TYPE);

        appendKeyInfo(doc, signatureElement, reference);
    }

    private Element createBinarySecurityToken(Document doc) throws CertificateEncodingException {
        Element binarySecurityToken = doc.createElementNS(WSSE_NS, "wsse:BinarySecurityToken");
        binarySecurityToken.setAttribute("EncodingType", BASE64_BINARY_ENCODING_TYPE);
        binarySecurityToken.setAttribute("ValueType", X509_V3_VALUE_TYPE);
        declareWsuId(binarySecurityToken, "X509-" + UUID.randomUUID());
        binarySecurityToken.setTextContent(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        return binarySecurityToken;
    }

    private void appendKeyIdentifierKeyInfo(Document doc, Element signatureElement) throws Exception {
        Element keyIdentifierElement = doc.createElementNS(WSSE_NS, "wsse:KeyIdentifier");
        keyIdentifierElement.setAttribute("EncodingType", BASE64_BINARY_ENCODING_TYPE);
        if (keyIdentifier.getValueType() == KeyIdentifierKeyInfo.ValueType.THUMBPRINT_SHA1) {
            keyIdentifierElement.setAttribute("ValueType", THUMBPRINT_SHA1_VALUE_TYPE);
            keyIdentifierElement.setTextContent(Base64.getEncoder().encodeToString(sha1Thumbprint(certificate)));
        } else {
            keyIdentifierElement.setAttribute("ValueType", X509_V3_VALUE_TYPE);
            keyIdentifierElement.setTextContent(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        }

        appendKeyInfo(doc, signatureElement, keyIdentifierElement);
    }

    /**
     * Appends a {@code ds:KeyInfo} holding a {@code wsse:SecurityTokenReference} that wraps
     * {@code tokenReference} - either a {@code wsse:Reference} or a {@code wsse:KeyIdentifier}.
     */
    private static void appendKeyInfo(Document doc, Element signatureElement, Element tokenReference) {
        Element securityTokenReferenceElement = doc.createElementNS(WSSE_NS, "wsse:SecurityTokenReference");
        declareWsuId(securityTokenReferenceElement, "STR-" + UUID.randomUUID());
        // WSS 1.1's TokenType says what kind of token the reference names. It is redundant next to a
        // wsse:Reference/KeyIdentifier that already carries a ValueType, but a ThumbprintSHA1
        // KeyIdentifier names the token by hash alone, and WSS4J/CXF read TokenType to learn what that
        // hash identifies. Set for every mode so the STR looks the same whichever one is configured.
        securityTokenReferenceElement.setAttributeNS(WSSE11_NS, "wsse11:TokenType", X509_V3_VALUE_TYPE);
        securityTokenReferenceElement.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsse11", WSSE11_NS);
        securityTokenReferenceElement.appendChild(tokenReference);

        Element keyInfo = doc.createElementNS(XMLSignature.XMLNS, "ds:KeyInfo");
        keyInfo.setAttribute("Id", "KI-" + UUID.randomUUID());
        keyInfo.appendChild(securityTokenReferenceElement);

        signatureElement.appendChild(keyInfo);
    }

    public List<SignatureReference> getReferences() {
        return references;
    }

    /**
     * @description The elements to sign. Each becomes one <code>ds:Reference</code> inside the
     * signature's <code>ds:SignedInfo</code> - except an <code>XPATH</code> reference matching more
     * than one element, which becomes one <code>ds:Reference</code> per matched element.
     */
    @MCChildElement(order = 1)
    public void setReferences(List<SignatureReference> references) {
        this.references = references == null ? List.of() : List.copyOf(references);
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * @description The XML Signature algorithm URI used to compute the signature.
     * @default http://www.w3.org/2001/04/xmldsig-more#rsa-sha256
     */
    @MCAttribute
    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    /**
     * @description The digest algorithm URI used for each <code>ds:Reference</code>.
     * @default http://www.w3.org/2001/04/xmlenc#sha256
     */
    @MCAttribute
    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public String getCanonicalizationAlgorithm() {
        return canonicalizationAlgorithm;
    }

    /**
     * @description The canonicalization algorithm URI used for <code>ds:SignedInfo</code>.
     * @default http://www.w3.org/2001/10/xml-exc-c14n#
     */
    @MCAttribute
    public void setCanonicalizationAlgorithm(String canonicalizationAlgorithm) {
        this.canonicalizationAlgorithm = canonicalizationAlgorithm;
    }

    public X509DataKeyInfo getX509Data() {
        return x509Data;
    }

    /**
     * @description Embeds the signing certificate inline in <code>ds:KeyInfo</code>. This is the
     * default when none of this, <code>securityTokenReference</code>, or <code>keyIdentifier</code>
     * is set.
     */
    @MCChildElement(order = 2)
    public void setX509Data(X509DataKeyInfo x509Data) {
        this.x509Data = x509Data;
    }

    public SecurityTokenReferenceKeyInfo getSecurityTokenReference() {
        return securityTokenReference;
    }

    /**
     * @description References the signing certificate from <code>ds:KeyInfo</code> via a
     * <code>wsse:SecurityTokenReference</code> pointing at a <code>wsse:BinarySecurityToken</code>,
     * instead of embedding it inline. Mutually exclusive with <code>x509Data</code> and
     * <code>keyIdentifier</code>.
     */
    @MCChildElement(order = 3)
    public void setSecurityTokenReference(SecurityTokenReferenceKeyInfo securityTokenReference) {
        this.securityTokenReference = securityTokenReference;
    }

    public KeyIdentifierKeyInfo getKeyIdentifier() {
        return keyIdentifier;
    }

    /**
     * @description References the signing certificate from <code>ds:KeyInfo</code> via a
     * <code>wsse:SecurityTokenReference</code>/<code>wsse:KeyIdentifier</code>, instead of
     * embedding it inline or via a separate <code>wsse:BinarySecurityToken</code>. Mutually
     * exclusive with <code>x509Data</code> and <code>securityTokenReference</code>.
     */
    @MCChildElement(order = 4)
    public void setKeyIdentifier(KeyIdentifierKeyInfo keyIdentifier) {
        this.keyIdentifier = keyIdentifier;
    }
}
