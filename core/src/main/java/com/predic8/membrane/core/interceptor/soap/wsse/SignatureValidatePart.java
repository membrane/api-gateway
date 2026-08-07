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
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.io.ByteArrayInputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;

/**
 * @description Verifies the XML Signature (<a href="http://www.w3.org/2000/09/xmldsig#">XML-DSig</a>)
 * in the inbound <code>wsse:Security</code> header. Beyond checking that the signature is
 * cryptographically valid, this validates the signing certificate against the enclosing
 * <code>wsSecurity</code> element's <code>truststore</code>, and confirms the signature actually
 * covers every element listed in <code>requiredReferences</code> — the defense against XML
 * Signature Wrapping attacks, where an attacker leaves a validly-signed but irrelevant fragment in
 * the message while the element downstream logic actually reads is unsigned or swapped. Freshness of
 * a <code>wsu:Timestamp</code> is only enforced when <code>requiredReferences</code> contains a
 * <code>TIMESTAMP</code> entry; without one, a captured signed message stays replayable.
 */
@MCElement(name = "signature", component = false, id = "wsSecurity-validate-signature")
public class SignatureValidatePart extends ValidatePart {

    private static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";

    private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofMinutes(5);

    private List<SignatureReference> requiredReferences = new ArrayList<>();
    private Duration clockSkew = DEFAULT_CLOCK_SKEW;

    private java.security.KeyStore trustKeyStore;

    @Override
    protected void init() {
        if (parent.getTrustStore() == null) {
            throw new ConfigurationException(
                    "wsSecurity validate/signature requires a <truststore> on the enclosing wsSecurity element.");
        }
        if (requiredReferences.isEmpty()) {
            throw new ConfigurationException(
                    "wsSecurity validate/signature requires at least one <requiredReferences> child element.");
        }
        requiredReferences.forEach(SignatureReference::validate);
        try {
            trustKeyStore = StaticSSLContext.openKeyStore(
                    parent.getTrustStore(), null, parent.getRouter().getResolverMap(), parent.beanBaseLocation());
        } catch (Exception e) {
            throw new ConfigurationException("Could not load the wsSecurity truststore.", e);
        }
    }

    @Override
    void process(WsSecurityContext ctx) throws Exception {
        Element security = ctx.security();
        Element signatureElement = findSingleSignature(security);
        if (signatureElement == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "wsse:Security carries no ds:Signature to verify.");
        }
        verify(ctx.document(), ctx.envelope(), security, ctx.soapNs(), signatureElement);
    }

    /**
     * @return the single {@code ds:Signature} inside {@code wsse:Security}, or null if there is none
     */
    private static Element findSingleSignature(Element security) {
        NodeList signatureNodes = security.getElementsByTagNameNS(DS_NS, "Signature");
        if (signatureNodes.getLength() == 0) {
            return null;
        }
        if (signatureNodes.getLength() > 1) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "More than one ds:Signature element found; rejecting as ambiguous.");
        }
        return (Element) signatureNodes.item(0);
    }

    private void verify(Document doc, Element envelope, Element security, String soapNs, Element signatureElement)
            throws Exception {
        rejectUnsignedSignatureContent(signatureElement);

        X509Certificate[] chain = resolveCertificateChain(doc, signatureElement);

        DOMValidateContext valContext = new DOMValidateContext(chain[0].getPublicKey(), signatureElement);
        XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(valContext);
        if (!signature.validate(valContext)) {
            throw new WsSecurityFaultException(FAILED_CHECK, "Signature is not cryptographically valid.");
        }

        checkTrusted(chain);

        for (SignatureReference required : requiredReferences) {
            checkRequiredReference(doc, envelope, security, soapNs, signature, required);
        }
    }

    // Validated as a plain PKIX certificate path, not via X509TrustManager.checkServerTrusted:
    // the latter additionally enforces the TLS server-authentication constraints (serverAuth EKU,
    // key-exchange-specific key usage), which a signing certificate has no reason to carry.
    private void checkTrusted(X509Certificate[] chain) {
        try {
            List<X509Certificate> path = pathBelowTrustAnchors(chain);
            if (path.isEmpty()) {
                // The signing certificate is itself a trust anchor - nothing left to validate.
                return;
            }
            PKIXParameters params = new PKIXParameters(trustKeyStore);
            params.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(
                    CertificateFactory.getInstance("X.509").generateCertPath(path), params);
        } catch (CertPathValidatorException | InvalidAlgorithmParameterException | KeyStoreException
                 | CertificateException | NoSuchAlgorithmException e) {
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "Signing certificate is not trusted: " + e.getMessage());
        }
    }

    /**
     * A {@code CertPath} must not contain the trust anchor itself, so drop the anchors the message
     * supplied at the end of its chain.
     */
    private List<X509Certificate> pathBelowTrustAnchors(X509Certificate[] chain) throws KeyStoreException {
        List<X509Certificate> path = new ArrayList<>();
        for (X509Certificate cert : chain) {
            if (trustKeyStore.getCertificateAlias(cert) != null) {
                break;
            }
            path.add(cert);
        }
        return path;
    }

    // The wsse:SecurityTokenReference path below only ever yields a single (leaf) certificate,
    // since that's all a wsse:BinarySecurityToken carries - there's no message-supplied
    // intermediate chain. Any intermediate CAs must therefore already be present as trust anchors
    // in the configured truststore itself.
    private X509Certificate[] resolveCertificateChain(Document doc, Element signatureElement) throws Exception {
        Element keyInfo = getFirstChildByName(signatureElement, DS_NS, "KeyInfo");
        if (keyInfo == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "ds:Signature has no ds:KeyInfo; cannot resolve the signing certificate.");
        }

        Element x509Data = getFirstChildByName(keyInfo, DS_NS, "X509Data");
        if (x509Data != null) {
            return chainFromX509Data(x509Data);
        }

        Element str = getFirstChildByName(keyInfo, WSSE_NS, "SecurityTokenReference");
        if (str != null) {
            return new X509Certificate[]{certificateFromSecurityTokenReference(doc, str)};
        }

        throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                "Unsupported ds:KeyInfo shape: expected ds:X509Data or wsse:SecurityTokenReference.");
    }

    private static X509Certificate[] chainFromX509Data(Element x509Data) throws Exception {
        List<Element> certElements = getChildrenByName(x509Data, DS_NS, "X509Certificate");
        if (certElements.isEmpty()) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN, "ds:X509Data has no ds:X509Certificate.");
        }
        X509Certificate[] chain = new X509Certificate[certElements.size()];
        for (int i = 0; i < certElements.size(); i++) {
            chain[i] = decodeCertificate(certElements.get(i).getTextContent());
        }
        return chain;
    }

    private X509Certificate certificateFromSecurityTokenReference(Document doc, Element str) throws Exception {
        Element keyIdentifier = getFirstChildByName(str, WSSE_NS, "KeyIdentifier");
        if (keyIdentifier != null) {
            return resolveByKeyIdentifier(keyIdentifier);
        }

        Element strReference = getFirstChildByName(str, WSSE_NS, "Reference");
        if (strReference == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                    "wsse:SecurityTokenReference has no wsse:Reference or wsse:KeyIdentifier.");
        }
        String uri = strReference.getAttribute("URI");
        if (!uri.startsWith("#")) {
            throw new WsSecurityFaultException(SECURITY_TOKEN_UNAVAILABLE,
                    "wsse:Reference URI must be a same-document reference.");
        }
        Element token = resolveUniqueElementById(doc, uri.substring(1), SECURITY_TOKEN_UNAVAILABLE);
        if (!(WSSE_NS.equals(token.getNamespaceURI()) && "BinarySecurityToken".equals(token.getLocalName()))) {
            throw new WsSecurityFaultException(SECURITY_TOKEN_UNAVAILABLE,
                    "wsse:Reference does not point at a wsse:BinarySecurityToken.");
        }
        return decodeCertificate(token.getTextContent());
    }

    // Unlike ds:X509Data/wsse:BinarySecurityToken (which carry the certificate itself), a
    // ThumbprintSHA1 wsse:KeyIdentifier only carries a hash - the certificate it names must
    // already be a trust anchor in the configured truststore, so it's resolved from there.
    private X509Certificate resolveByKeyIdentifier(Element keyIdentifier) throws Exception {
        String valueType = keyIdentifier.getAttribute("ValueType");
        String content = keyIdentifier.getTextContent().replaceAll("\\s", "");
        if (THUMBPRINT_SHA1_VALUE_TYPE.equals(valueType)) {
            return findCertificateByThumbprint(decodeBase64(content));
        }
        if (!X509_V3_VALUE_TYPE.equals(valueType)) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                    "Unsupported wsse:KeyIdentifier ValueType: \"" + valueType + "\".");
        }
        return decodeCertificate(content);
    }

    private X509Certificate findCertificateByThumbprint(byte[] thumbprint) throws Exception {
        Enumeration<String> aliases = trustKeyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (trustKeyStore.getCertificate(alias) instanceof X509Certificate cert
                && Arrays.equals(thumbprint, sha1Thumbprint(cert))) {
                return cert;
            }
        }
        throw new WsSecurityFaultException(SECURITY_TOKEN_UNAVAILABLE,
                "No certificate in the truststore matches the wsse:KeyIdentifier thumbprint.");
    }

    private static X509Certificate decodeCertificate(String base64) throws CertificateException {
        // Base64.getDecoder() rejects embedded whitespace/line breaks, which the certificate's
        // text content commonly has (e.g. from pretty-printing) - strip it all before decoding.
        byte[] der = decodeBase64(base64.replaceAll("\\s", ""));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    // Base64.getDecoder() signals malformed input with IllegalArgumentException. That content is
    // attacker-supplied, so it has to end up in the fault path rather than escaping as an internal
    // error.
    private static byte[] decodeBase64(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                    "Malformed base64 content in wsse:Security.");
        }
    }

    // Only ds:SignedInfo is protected by ds:SignatureValue. Everything else inside ds:Signature -
    // ds:Object and its ds:Manifest children in particular - is unsigned, so an attacker can add,
    // remove or rewrite it at will while the signature still validates. Since this part supports no
    // legitimate use for such content, it is refused outright rather than ignored: that removes the
    // "park the genuinely signed element in a ds:Object" half of an XML Signature Wrapping attack
    // (the reference half is handled in checkRequiredElement).
    private static void rejectUnsignedSignatureContent(Element signatureElement) {
        NodeList children = signatureElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                && DS_NS.equals(child.getNamespaceURI())
                && ("Object".equals(child.getLocalName()) || "Manifest".equals(child.getLocalName()))) {
                throw new WsSecurityFaultException(FAILED_CHECK,
                        "ds:Signature carries unsigned ds:" + child.getLocalName() + " content; rejecting.");
            }
        }
    }

    private void checkRequiredReference(Document doc, Element envelope, Element security, String soapNs,
                                        XMLSignature signature, SignatureReference required) {
        try {
            for (Element expected : resolveReference(doc, envelope, security, soapNs, required, parent.getXmlConfig())) {
                checkRequiredElement(doc, signature, required, expected);
            }
        } catch (WsSecurityXmlUtil.ReferenceResolutionException e) {
            throw new WsSecurityFaultException(FAILED_CHECK, "[" + describe(required) + "] " + e.getMessage(), e);
        } catch (WsSecurityFaultException e) {
            throw new WsSecurityFaultException(e.getCode(), "[" + describe(required) + "] " + e.getMessage(), e);
        }
    }

    private static String describe(SignatureReference required) {
        return required.getBy() == SignatureReference.By.XPATH
                ? "XPATH " + required.getXpath()
                : required.getBy().toString();
    }

    private void checkRequiredElement(Document doc, XMLSignature signature, SignatureReference required, Element expected) {
        if (required.getBy() == SignatureReference.By.TIMESTAMP) {
            checkTimestampFreshness(expected);
        }
        String expectedId = idOf(expected);
        if (expectedId.isEmpty()) {
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "Required element (" + required.getBy() + ") has no wsu:Id/Id, so it cannot be covered by the signature.");
        }

        // First half of the signature-wrapping defense: reject outright if expectedId is used by
        // more than one element anywhere in the document. An attacker who moves the genuinely
        // signed element aside and plants a decoy at the real structural position, giving it the
        // same Id to "borrow" the existing signature's coverage, makes the Id ambiguous - which
        // resolveUniqueElementById refuses to accept.
        resolveUniqueElementById(doc, expectedId, FAILED_CHECK);

        // Second half: a decoy carrying a fresh, different Id is caught here, because that Id
        // appears in no ds:Reference of the signature. Crucially, the references are taken from the
        // unmarshalled ds:SignedInfo of the signature that validate() just verified - NOT by
        // searching the ds:Signature subtree in the DOM. Only ds:SignedInfo is covered by
        // ds:SignatureValue, so a DOM-wide search would also accept an attacker-planted
        // ds:Reference sitting in unsigned ds:Object/ds:Manifest content, letting a wrapped message
        // declare its own decoy as "covered".
        if (!isCoveredBy(signature, expectedId)) {
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "Required element (" + required.getBy() + ", Id=" + expectedId + ") is not covered by any ds:Reference.");
        }
    }

    private static boolean isCoveredBy(XMLSignature signature, String expectedId) {
        return signature.getSignedInfo().getReferences().stream()
                .anyMatch(reference -> reference instanceof Reference ref && ("#" + expectedId).equals(ref.getURI()));
    }

    // Signed-Timestamp is the standard WS-Security defense against replaying a captured,
    // validly-signed message: without checking Created/Expires here, a signature covering the
    // Timestamp is only proof the message existed at some point, not that it is still fresh.
    private void checkTimestampFreshness(Element timestamp) {
        Instant created = parseTimestampInstant(timestamp, "Created");
        Instant expires = parseTimestampInstant(timestamp, "Expires");
        Instant now = Instant.now();
        // Created itself must be recent - this is what catches a stale Timestamp with no Expires
        // at all, not just one whose Expires has passed.
        if (created.isBefore(now.minus(clockSkew)) || created.isAfter(now.plus(clockSkew))) {
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "wsu:Timestamp Created (" + created + ") is outside the allowed clock skew.");
        }
        if (expires != null && expires.isBefore(now.minus(clockSkew))) {
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "wsu:Timestamp has expired (Expires=" + expires + ").");
        }
    }

    private static Instant parseTimestampInstant(Element timestamp, String localName) {
        Element el = getFirstChildByName(timestamp, WSU_NS, localName);
        if (el == null) {
            if ("Created".equals(localName)) {
                throw new WsSecurityFaultException(FAILED_CHECK, "wsu:Timestamp has no wsu:Created.");
            }
            return null;
        }
        try {
            return OffsetDateTime.parse(el.getTextContent().trim()).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "wsu:Timestamp/wsu:" + localName + " is not a valid xs:dateTime: " + el.getTextContent());
        }
    }

    /**
     * @param ambiguityCode the fault to report when the id is missing or used more than once - a
     *                      failed check when it is a required reference, an unavailable token when
     *                      it is a {@code wsse:Reference} target
     */
    private static Element resolveUniqueElementById(Document doc, String id, WsSecurityFaultCode ambiguityCode) {
        List<Element> matches = new ArrayList<>();
        forEachDescendantElement(doc.getDocumentElement(), element -> {
            if (id.equals(idOf(element))) {
                matches.add(element);
            }
        });
        if (matches.isEmpty()) {
            throw new WsSecurityFaultException(ambiguityCode, "No element found with Id \"" + id + "\".");
        }
        if (matches.size() > 1) {
            throw new WsSecurityFaultException(ambiguityCode,
                    "Id \"" + id + "\" is used by more than one element; rejecting as ambiguous.");
        }
        return matches.getFirst();
    }

    public List<SignatureReference> getRequiredReferences() {
        return requiredReferences;
    }

    /**
     * @description The elements that must be covered by the signature. Verification fails if any
     * of these is not referenced by the signature, or if the referenced element is not the same
     * element found by structural navigation (the defense against signature wrapping attacks).
     */
    @MCChildElement(order = 1)
    public void setRequiredReferences(List<SignatureReference> requiredReferences) {
        this.requiredReferences = requiredReferences;
    }

    public String getClockSkew() {
        return clockSkew.toString();
    }

    /**
     * @description Tolerance, as an ISO-8601 duration, applied when checking a required
     * <code>TIMESTAMP</code> reference's <code>Created</code>/<code>Expires</code> against the
     * current time. Only relevant when <code>requiredReferences</code> includes a
     * <code>TIMESTAMP</code> entry.
     * @default PT5M
     */
    @MCAttribute
    public void setClockSkew(String clockSkew) {
        Duration parsed = Duration.parse(clockSkew);
        if (parsed.isNegative()) {
            throw new ConfigurationException("clockSkew must not be negative.");
        }
        this.clockSkew = parsed;
    }
}
