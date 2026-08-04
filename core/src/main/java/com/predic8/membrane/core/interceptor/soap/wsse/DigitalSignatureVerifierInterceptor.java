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
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.SOAPUtil;
import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.THUMBPRINT_SHA1_VALUE_TYPE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.getChildrenByName;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.getFirstChildByName;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.resolveReference;

/**
 * @description Verifies the XML Signature (<a href="http://www.w3.org/2000/09/xmldsig#">XML-DSig</a>)
 * on an incoming SOAP request, as added by e.g. the <code>digitalSignature</code> interceptor.
 * Beyond checking that the signature is cryptographically valid, this validates the signing
 * certificate against the configured truststore, and confirms the signature actually covers every
 * element listed in <code>requiredReferences</code> - the defense against XML Signature Wrapping
 * attacks, where an attacker leaves a validly-signed but irrelevant fragment in the message while
 * the element downstream logic actually reads is unsigned or swapped. A missing signature returns
 * 401; an invalid, untrusted, or incomplete one returns 403, both as Problem Details. Only acts on
 * requests.
 * @topic 3. Security
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - digitalSignatureVerifier:
 *         truststore:
 *           location: partner-ca.p12
 *           password: secret
 *         requiredReferences:
 *           - by: BODY
 * </code></pre>
 */
@MCElement(name = "digitalSignatureVerifier")
public class DigitalSignatureVerifierInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DigitalSignatureVerifierInterceptor.class);

    private static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";

    private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofMinutes(5);

    private TrustStore trustStore;
    private List<SignatureReference> requiredReferences = new ArrayList<>();
    private Duration clockSkew = DEFAULT_CLOCK_SKEW;

    private X509TrustManager trustManager;
    private java.security.KeyStore trustKeyStore;

    @Override
    public void init() {
        super.init();
        if (trustStore == null) {
            throw new ConfigurationException("digitalSignatureVerifier requires a <truststore> child element.");
        }
        if (requiredReferences.isEmpty()) {
            throw new ConfigurationException("digitalSignatureVerifier requires at least one <requiredReferences> child element.");
        }
        try {
            trustKeyStore = StaticSSLContext.openKeyStore(
                    trustStore, null, router.getResolverMap(), getBeanBaseLocation());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustKeyStore);
            trustManager = (X509TrustManager) tmf.getTrustManagers()[0];
        } catch (Exception e) {
            throw new ConfigurationException("Could not load truststore for digitalSignatureVerifier interceptor.", e);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), exc.getRequest()).isSOAP()) {
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Not a SOAP message.")
                    .detail("Request body is not XML or does not contain a SOAP body, so it could not be verified.")
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        try {
            Document doc = HardenedXmlParser.getInstance().parse(XMLUtil.getInputSource(exc.getRequest()));
            // A freshly (DTD-less) parsed document doesn't know which attribute is an XML ID, so
            // same-document "#id" dereferencing - both the JSR-105 signature validation below and
            // getElementById-style lookups - would otherwise fail to resolve anything.
            markWsuIdAttributes(doc.getDocumentElement());

            Element envelope = doc.getDocumentElement();
            String soapNs = envelope.getNamespaceURI();

            Element header = getFirstChildByName(envelope, soapNs, "Header");
            Element security = header == null ? null : getFirstChildByName(header, WSSE_NS, "Security");

            NodeList signatureNodes = security == null ? null : security.getElementsByTagNameNS(DS_NS, "Signature");
            if (security == null || signatureNodes.getLength() == 0) {
                security(router.getConfiguration().isProduction(), getDisplayName())
                        .title("Signature missing.")
                        .status(401)
                        .detail("Request has no wsse:Security/ds:Signature to verify.")
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            if (signatureNodes.getLength() > 1) {
                throw new VerificationException("More than one ds:Signature element found; rejecting as ambiguous.");
            }
            Element signatureElement = (Element) signatureNodes.item(0);

            X509Certificate[] chain = resolveCertificateChain(doc, signatureElement);

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            DOMValidateContext valContext = new DOMValidateContext(chain[0].getPublicKey(), signatureElement);
            XMLSignature signature = fac.unmarshalXMLSignature(valContext);
            if (!signature.validate(valContext)) {
                throw new VerificationException("Signature is not cryptographically valid.");
            }

            try {
                trustManager.checkServerTrusted(chain, chain[0].getPublicKey().getAlgorithm());
            } catch (CertificateException e) {
                throw new VerificationException("Signing certificate is not trusted: " + e.getMessage());
            }

            for (SignatureReference required : requiredReferences) {
                checkRequiredReference(doc, envelope, security, soapNs, signatureElement, required);
            }

            return CONTINUE;
        } catch (VerificationException | WsSecurityXml.ReferenceResolutionException e) {
            security(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Signature verification failed.")
                    .status(403)
                    .detail(e.getMessage())
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (Exception e) {
            log.warn("Could not verify ds:Signature on SOAP body", e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not verify signature on SOAP message.")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    // The wsse:SecurityTokenReference path below only ever yields a single (leaf) certificate,
    // since that's all a wsse:BinarySecurityToken carries - there's no message-supplied
    // intermediate chain. Any intermediate CAs must therefore already be present as trust anchors
    // in the configured truststore itself.
    private X509Certificate[] resolveCertificateChain(Document doc, Element signatureElement) throws Exception {
        Element keyInfo = getFirstChildByName(signatureElement, DS_NS, "KeyInfo");
        if (keyInfo == null) {
            throw new VerificationException("ds:Signature has no ds:KeyInfo; cannot resolve the signing certificate.");
        }

        Element x509Data = getFirstChildByName(keyInfo, DS_NS, "X509Data");
        if (x509Data != null) {
            List<Element> certElements = getChildrenByName(x509Data, DS_NS, "X509Certificate");
            if (certElements.isEmpty()) {
                throw new VerificationException("ds:X509Data has no ds:X509Certificate.");
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate[] chain = new X509Certificate[certElements.size()];
            for (int i = 0; i < certElements.size(); i++) {
                chain[i] = decodeCertificate(cf, certElements.get(i).getTextContent());
            }
            return chain;
        }

        Element str = getFirstChildByName(keyInfo, WSSE_NS, "SecurityTokenReference");
        if (str != null) {
            Element keyIdentifier = getFirstChildByName(str, WSSE_NS, "KeyIdentifier");
            if (keyIdentifier != null) {
                return new X509Certificate[]{resolveByKeyIdentifier(keyIdentifier)};
            }

            Element strReference = getFirstChildByName(str, WSSE_NS, "Reference");
            if (strReference == null) {
                throw new VerificationException("wsse:SecurityTokenReference has no wsse:Reference or wsse:KeyIdentifier.");
            }
            String uri = strReference.getAttribute("URI");
            if (!uri.startsWith("#")) {
                throw new VerificationException("wsse:Reference URI must be a same-document reference.");
            }
            Element token = resolveUniqueElementById(doc, uri.substring(1));
            if (!(WSSE_NS.equals(token.getNamespaceURI()) && "BinarySecurityToken".equals(token.getLocalName()))) {
                throw new VerificationException("wsse:Reference does not point at a wsse:BinarySecurityToken.");
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return new X509Certificate[]{decodeCertificate(cf, token.getTextContent())};
        }

        throw new VerificationException("Unsupported ds:KeyInfo shape: expected ds:X509Data or wsse:SecurityTokenReference.");
    }

    // Unlike ds:X509Data/wsse:BinarySecurityToken (which carry the certificate itself), a
    // ThumbprintSHA1 wsse:KeyIdentifier only carries a hash - the certificate it names must
    // already be a trust anchor in the configured truststore, so it's resolved from there.
    private X509Certificate resolveByKeyIdentifier(Element keyIdentifier) throws Exception {
        String valueType = keyIdentifier.getAttribute("ValueType");
        String content = keyIdentifier.getTextContent().replaceAll("\\s", "");
        if (THUMBPRINT_SHA1_VALUE_TYPE.equals(valueType)) {
            byte[] thumbprint = Base64.getDecoder().decode(content);
            return findCertificateByThumbprint(thumbprint);
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return decodeCertificate(cf, content);
    }

    private X509Certificate findCertificateByThumbprint(byte[] thumbprint) throws Exception {
        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
        java.util.Enumeration<String> aliases = trustKeyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (trustKeyStore.getCertificate(alias) instanceof X509Certificate cert
                    && java.util.Arrays.equals(thumbprint, sha1.digest(cert.getEncoded()))) {
                return cert;
            }
        }
        throw new VerificationException("No certificate in the truststore matches the wsse:KeyIdentifier thumbprint.");
    }

    private static X509Certificate decodeCertificate(CertificateFactory cf, String base64) throws CertificateException {
        // Base64.getDecoder() rejects embedded whitespace/line breaks, which the certificate's
        // text content commonly has (e.g. from pretty-printing) - strip it all before decoding.
        byte[] der = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
    }

    private void checkRequiredReference(Document doc, Element envelope, Element security, String soapNs,
                                         Element signatureElement, SignatureReference required) {
        Element expected = resolveReference(doc, envelope, security, soapNs, required);
        if (required.getBy() == SignatureReference.By.TIMESTAMP) {
            checkTimestampFreshness(expected);
        }
        String expectedId = idOf(expected);
        if (expectedId.isEmpty()) {
            throw new VerificationException(
                    "Required element (" + required.getBy() + ") has no wsu:Id/Id, so it cannot be covered by the signature.");
        }

        // Reject outright if expectedId is used by more than one element anywhere in the document.
        // This is the actual defense against signature wrapping: an attacker who moves the
        // genuinely signed element aside and plants a decoy at the real structural position,
        // giving it the same Id to "borrow" the existing signature's coverage, makes the Id
        // ambiguous - which resolveUniqueElementById refuses to accept. (A decoy using a fresh,
        // different Id is instead caught below: it's simply not covered by any ds:Reference.)
        resolveUniqueElementById(doc, expectedId);

        NodeList signedInfoReferences = signatureElement.getElementsByTagNameNS(DS_NS, "Reference");
        boolean covered = false;
        for (int i = 0; i < signedInfoReferences.getLength(); i++) {
            Element reference = (Element) signedInfoReferences.item(i);
            if (("#" + expectedId).equals(reference.getAttribute("URI"))) {
                covered = true;
                break;
            }
        }
        if (!covered) {
            throw new VerificationException(
                    "Required element (" + required.getBy() + ", Id=" + expectedId + ") is not covered by any ds:Reference.");
        }
    }

    // Signed-Timestamp is the standard WS-Security defense against replaying a captured,
    // validly-signed request: without checking Created/Expires here, a signature covering the
    // Timestamp is only proof the message existed at some point, not that it is still fresh.
    private void checkTimestampFreshness(Element timestamp) {
        Instant created = parseTimestampInstant(timestamp, "Created");
        Instant expires = parseTimestampInstant(timestamp, "Expires");
        Instant now = Instant.now();
        // Created itself must be recent - this is what catches a stale Timestamp with no Expires
        // at all, not just one whose Expires has passed.
        if (created.isBefore(now.minus(clockSkew)) || created.isAfter(now.plus(clockSkew))) {
            throw new VerificationException("wsu:Timestamp Created (" + created + ") is outside the allowed clock skew.");
        }
        if (expires != null && expires.isBefore(now.minus(clockSkew))) {
            throw new VerificationException("wsu:Timestamp has expired (Expires=" + expires + ").");
        }
    }

    private Instant parseTimestampInstant(Element timestamp, String localName) {
        Element el = getFirstChildByName(timestamp, WSU_NS, localName);
        if (el == null) {
            if ("Created".equals(localName)) {
                throw new VerificationException("wsu:Timestamp has no wsu:Created.");
            }
            return null;
        }
        try {
            return Instant.parse(el.getTextContent());
        } catch (DateTimeParseException e) {
            throw new VerificationException("wsu:Timestamp/wsu:" + localName + " is not a valid xs:dateTime: " + el.getTextContent());
        }
    }

    private static String idOf(Element element) {
        String wsuId = element.getAttributeNS(WSU_NS, "Id");
        return !wsuId.isEmpty() ? wsuId : element.getAttribute("Id");
    }

    private static Element resolveUniqueElementById(Document doc, String id) {
        List<Element> matches = new ArrayList<>();
        collectElementsById(doc.getDocumentElement(), id, matches);
        if (matches.isEmpty()) {
            throw new VerificationException("No element found with Id \"" + id + "\".");
        }
        if (matches.size() > 1) {
            throw new VerificationException("Id \"" + id + "\" is used by more than one element; rejecting as ambiguous.");
        }
        return matches.getFirst();
    }

    private static void collectElementsById(Element element, String id, List<Element> out) {
        if (id.equals(idOf(element))) {
            out.add(element);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                collectElementsById(child, id, out);
            }
        }
    }

    private static void markWsuIdAttributes(Element element) {
        if (!element.getAttributeNS(WSU_NS, "Id").isEmpty()) {
            element.setIdAttributeNS(WSU_NS, "Id", true);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                markWsuIdAttributes(child);
            }
        }
    }

    private static class VerificationException extends RuntimeException {
        VerificationException(String message) {
            super(message);
        }
    }

    public TrustStore getTrustStore() {
        return trustStore;
    }

    /**
     * @description The truststore holding the CA certificates used to validate the signing
     * certificate's chain of trust.
     */
    @MCChildElement(order = 1)
    public void setTrustStore(TrustStore trustStore) {
        this.trustStore = trustStore;
    }

    public List<SignatureReference> getRequiredReferences() {
        return requiredReferences;
    }

    /**
     * @description The elements that must be covered by the signature. Verification fails if any
     * of these is not referenced by the signature, or if the referenced element is not the same
     * element found by structural navigation (the defense against signature wrapping attacks).
     */
    @MCChildElement(order = 2)
    public void setRequiredReferences(List<SignatureReference> requiredReferences) {
        this.requiredReferences = requiredReferences;
    }

    public String getClockSkew() {
        return clockSkew.toString();
    }

    /**
     * @description Tolerance, as an ISO-8601 duration, applied when checking a required
     * <code>TIMESTAMP</code> reference's <code>Created</code>/<code>Expires</code> against the
     * current time. Only relevant when <code>requiredReferences</code> includes a <code>TIMESTAMP</code>
     * entry.
     * @default PT5M
     */
    @MCAttribute
    public void setClockSkew(String clockSkew) {
        this.clockSkew = Duration.parse(clockSkew);
    }
}
