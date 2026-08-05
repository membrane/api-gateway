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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
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
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Stream;

import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.*;

/**
 * @description Adds an XML Signature (<a href="http://www.w3.org/2000/09/xmldsig#">XML-DSig</a>)
 * to a SOAP request's header, signing the elements listed in <code>references</code>. Follows the
 * WS-Security convention used by Apache CXF: the <code>ds:Signature</code> is placed inside
 * <code>wsse:Security</code>, and each signed element receives a <code>wsu:Id</code> that a
 * detached <code>ds:Reference</code> points at. A non-SOAP body or an unresolvable reference
 * returns 400 as Problem Details. Only acts on requests.
 * @topic 3. Security
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - digitalSignature:
 *         keystore:
 *           location: signing.p12
 *           password: secret
 *         references:
 *           - by: BODY
 * </code></pre>
 */
@MCElement(name = "digitalSignature")
public class DigitalSignatureInterceptor extends AbstractSoapDomInterceptor {

    private static final String DEFAULT_SIGNATURE_ALGORITHM = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    private static final String DEFAULT_DIGEST_ALGORITHM = "http://www.w3.org/2001/04/xmlenc#sha256";
    private static final String DEFAULT_CANONICALIZATION_ALGORITHM = CanonicalizationMethod.EXCLUSIVE;

    private KeyStore keyStore;
    private List<SignatureReference> references = new ArrayList<>();
    private String signatureAlgorithm = DEFAULT_SIGNATURE_ALGORITHM;
    private String digestAlgorithm = DEFAULT_DIGEST_ALGORITHM;
    private String canonicalizationAlgorithm = DEFAULT_CANONICALIZATION_ALGORITHM;
    private X509DataKeyInfo x509Data;
    private SecurityTokenReferenceKeyInfo securityTokenReference;
    private KeyIdentifierKeyInfo keyIdentifier;

    private PrivateKey privateKey;
    private X509Certificate certificate;

    @Override
    public void init() {
        super.init();
        validateConfiguration();
        loadSigningMaterial();
    }

    private void validateConfiguration() {
        if (keyStore == null) {
            throw new ConfigurationException("digitalSignature requires a <keystore> child element.");
        }
        if (references.isEmpty()) {
            throw new ConfigurationException("digitalSignature requires at least one <reference> child element.");
        }
        if (Stream.of(x509Data, securityTokenReference, keyIdentifier).filter(Objects::nonNull).count() > 1) {
            throw new ConfigurationException(
                    "digitalSignature accepts at most one of <x509Data>, <securityTokenReference>, or <keyIdentifier>.");
        }
    }

    private void loadSigningMaterial() {
        try {
            char[] keyPassword = resolveKeyPassword();
            java.security.KeyStore ks = StaticSSLContext.openKeyStore(
                    keyStore, keyPassword, router.getResolverMap(), getBeanBaseLocation());

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
            throw new ConfigurationException("Could not load signing key from keystore for digitalSignature interceptor.", e);
        }
    }

    // For a PKCS12 keystore, the key is commonly protected by the same password as the store
    // itself; fall back to it when no distinct keyPassword is configured.
    private char[] resolveKeyPassword() {
        if (keyStore.getKeyPassword() != null) {
            return keyStore.getKeyPassword().toCharArray();
        }
        if (keyStore.getPassword() != null) {
            return keyStore.getPassword().toCharArray();
        }
        return "changeit".toCharArray();
    }

    @Override
    protected String notSoapDetail() {
        return "it could not be signed.";
    }

    @Override
    protected String internalErrorDetail() {
        return "Could not sign SOAP message.";
    }

    @Override
    protected Outcome handleDocument(Exchange exc, Document doc) throws Exception {
        Element envelope = doc.getDocumentElement();
        String soapNs = envelope.getNamespaceURI();

        Element security = getOrCreateSecurity(doc, getOrCreateHeader(doc, envelope, soapNs));
        ensureMustUnderstand(security, soapNs);

        List<String> referencedIds = new ArrayList<>();
        try {
            for (SignatureReference reference : references) {
                referencedIds.add(ensureId(resolveReference(doc, envelope, security, soapNs, reference), reference));
            }
        } catch (WsSecurityXml.ReferenceResolutionException e) {
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Could not resolve signature reference.")
                    .detail(e.getMessage())
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        sign(doc, envelope, security, referencedIds);

        writeBack(exc, doc);
        return CONTINUE;
    }

    private static String ensureId(Element target, SignatureReference reference) {
        String id = reference.getId() != null ? reference.getId()
                : existingId(target).orElseGet(() -> "sig-" + UUID.randomUUID());
        // declareWsuId's namespace declaration matters here too: it would otherwise only be added
        // as a namespace-fixup by the serializer, i.e. after the signature's digests are computed,
        // so canonicalization at signing time must already see it.
        declareWsuId(target, id);
        target.setIdAttributeNS(WSU_NS, "Id", true);
        return id;
    }

    private static Optional<String> existingId(Element target) {
        String wsuId = target.getAttributeNS(WSU_NS, "Id");
        String id = !wsuId.isEmpty() ? wsuId : target.getAttribute("Id");
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    private void sign(Document doc, Element envelope, Element security, List<String> referencedIds) throws Exception {
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        // Guards SignedInfo's own canonical form against the ancestor SOAP envelope prefix being
        // rewritten in transit (WSS4J/CXF convention); the per-reference Transform below instead
        // uses an empty prefix list, i.e. pure exclusive c14n for just the referenced element.
        String soapPrefix = envelope.getPrefix();
        ExcC14NParameterSpec signedInfoC14nSpec = new ExcC14NParameterSpec(
                soapPrefix == null || soapPrefix.isEmpty() ? List.of() : List.of(soapPrefix));

        DigestMethod digestMethod = fac.newDigestMethod(digestAlgorithm, null);
        Transform transform = fac.newTransform(canonicalizationAlgorithm, new ExcC14NParameterSpec(List.of()));

        List<Reference> refs = new ArrayList<>();
        for (String id : referencedIds) {
            refs.add(fac.newReference("#" + id, digestMethod, List.of(transform), null, null));
        }

        SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(canonicalizationAlgorithm, (C14NMethodParameterSpec) signedInfoC14nSpec),
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
        }
    }

    private void appendSecurityTokenReferenceKeyInfo(Document doc, Element security, Element signatureElement) throws Exception {
        String tokenId = "X509-" + UUID.randomUUID();
        Element binarySecurityToken = doc.createElementNS(WSSE_NS, "wsse:BinarySecurityToken");
        binarySecurityToken.setAttribute("EncodingType", BASE64_BINARY_ENCODING_TYPE);
        binarySecurityToken.setAttribute("ValueType", X509_V3_VALUE_TYPE);
        declareWsuId(binarySecurityToken, tokenId);
        binarySecurityToken.setTextContent(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        security.insertBefore(binarySecurityToken, signatureElement);

        Element reference = doc.createElementNS(WSSE_NS, "wsse:Reference");
        reference.setAttribute("URI", "#" + tokenId);
        reference.setAttribute("ValueType", X509_V3_VALUE_TYPE);

        appendKeyInfo(doc, signatureElement, reference);
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
        securityTokenReferenceElement.appendChild(tokenReference);

        Element keyInfo = doc.createElementNS(XMLSignature.XMLNS, "ds:KeyInfo");
        keyInfo.setAttribute("Id", "KI-" + UUID.randomUUID());
        keyInfo.appendChild(securityTokenReferenceElement);

        signatureElement.appendChild(keyInfo);
    }

    private static void declareWsuId(Element element, String id) {
        element.setAttributeNS(WSU_NS, "wsu:Id", id);
        if (element.lookupNamespaceURI("wsu") == null) {
            element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsu", WSU_NS);
        }
    }

    private static void ensureMustUnderstand(Element security, String soapNs) {
        if (!security.getAttributeNS(soapNs, "mustUnderstand").isEmpty()) {
            return;
        }
        security.setAttributeNS(soapNs, soapPrefixOrDeclare(security, soapNs) + ":mustUnderstand",
                SOAP12_NS.equals(soapNs) ? "true" : "1");
    }

    private static String soapPrefixOrDeclare(Element security, String soapNs) {
        String soapPrefix = security.lookupPrefix(soapNs);
        if (soapPrefix != null) {
            return soapPrefix;
        }
        security.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:soapenv", soapNs);
        return "soapenv";
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * @description The keystore holding the private key and certificate used to sign the
     * referenced elements.
     */
    @MCChildElement(order = 1)
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public List<SignatureReference> getReferences() {
        return references;
    }

    /**
     * @description The elements to sign. Each becomes one <code>ds:Reference</code> inside the
     * signature's <code>ds:SignedInfo</code>.
     */
    @MCChildElement(order = 2)
    public void setReferences(List<SignatureReference> references) {
        this.references = references;
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
    @MCChildElement(order = 3)
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
    @MCChildElement(order = 4)
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
    @MCChildElement(order = 5)
    public void setKeyIdentifier(KeyIdentifierKeyInfo keyIdentifier) {
        this.keyIdentifier = keyIdentifier;
    }
}
