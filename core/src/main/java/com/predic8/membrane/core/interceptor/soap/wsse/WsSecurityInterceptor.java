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
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.config.xml.XmlConfig;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.XmlDomBody;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.SOAPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.INVALID_SECURITY;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.XmlEncryptionUtil.XENC_NS;

/**
 * @description <p>Owns the WS-Security (<code>wsse:Security</code>) header of a SOAP message: the
 * <code>validate</code> list consumes the security the peer sent, the <code>secure</code> list
 * applies fresh security for the next hop. Both lists are optional and run in the order they are
 * written; the whole <code>validate</code> list runs first, and the inbound header is removed at
 * that boundary before <code>secure</code> creates a new one. With no <code>validate</code> list the
 * header is emptied of the tokens the peer sent instead of being forwarded unchecked; what survives is
 * only what asserts nothing on its own — its <code>wsu:Timestamp</code>, since a <code>secure</code>
 * signature may cover it, and its XML Encryption material (<code>xenc:EncryptedKey</code> and
 * <code>xenc:EncryptedData</code>) for as long as encrypted content is still in the message, since
 * dropping key material while forwarding the ciphertext would leave a message nobody downstream can
 * read. Header blocks targeted at a different <code>actor</code> are left untouched.</p>
 * <p>Signing and encrypting draw on opposite stores: a signature uses the <code>keystore</code>'s
 * private key and is verified against the <code>truststore</code>, while <code>encrypt</code> uses a
 * <code>truststore</code> certificate (the recipient's) and <code>decrypt</code> the
 * <code>keystore</code>'s private key.</p>
 * <p>Direction is not part of this element: nest it in <code>request</code> or <code>response</code>
 * to say which message it applies to. A gateway commonly validates what a client sent and
 * re-secures for the backend in one element, and mirrors that on the way back. Use two elements
 * with a transformation between them when the body has to change between validating and
 * re-securing.</p>
 * <p>A failing check answers with a <code>soap:Fault</code> matching the envelope version of the
 * offending message, carrying the WS-Security fault code (<code>wsse:FailedAuthentication</code>,
 * <code>wsse:FailedCheck</code>, and so on); a body that is not SOAP at all answers with Problem
 * Details, since no fault envelope can be produced for it.</p>
 * <h3>Apache CXF compatibility</h3>
 * <p>Interoperability is tested with Apache CXF. Membrane supports SOAP 1.1 and SOAP 1.2 in
 * both directions: it can secure requests for CXF and validate its secured responses, or
 * validate CXF requests and secure responses for CXF. Compatible security features include:</p>
 * <ul>
 * <li>Signing the SOAP body and timestamp with RSA-SHA256 and SHA-256 digests, using a
 * <code>wsse:SecurityTokenReference</code> pointing to an embedded certificate.</li>
 * <li>UsernameToken authentication with <code>PasswordText</code>
 * (Membrane <code>passwordType: PLAIN_TEXT</code>).</li>
 * <li>Encrypting SOAP body content with AES-256-GCM and XML Encryption 1.1 RSA-OAEP key
 * transport using SHA-256 and MGF1-SHA256, with a certificate thumbprint key identifier.</li>
 * <li>Signing the body and timestamp before encrypting the body.</li>
 * </ul>
 * <p>For signatures, add <code>securityTokenReference</code> to
 * Membrane's outbound <code>signature</code> and configure WSS4J's signature key identifier as
 * <code>DirectReference</code>. On Membrane, secure in the order <code>timestamp</code>,
 * <code>signature</code>, then optionally <code>encrypt</code>; validate in the order
 * <code>decrypt</code> when encrypted, <code>timestamp</code>, then <code>signature</code>.
 * Configure WSS4J with the action string <code>Signature Timestamp</code> or
 * <code>Signature Timestamp Encrypt</code> to place the timestamp before the signature
 * in the security header. Require signature coverage of both body and timestamp, and body
 * content encryption when using encryption, on the receiving side.</p>
 * @topic 3. Security
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - request:
 *         - wsSecurity:
 *             keystore:
 *               location: signing.p12
 *               password: secret
 *             truststore:
 *               location: partner-ca.p12
 *               password: secret
 *             validate:
 *               - decrypt:
 *                   requiredReferences:
 *                     - by: BODY
 *               - timestamp:
 *                   clockSkew: PT1M
 *               - usernameToken:
 *                   username: ${property.apiUser}
 *                   password: ${property.apiPassword}
 *               - signature:
 *                   requiredReferences:
 *                     - by: BODY
 *                     - by: TIMESTAMP
 *                     - by: USERNAME_TOKEN
 *             secure:
 *               - timestamp:
 *                   ttl: PT5M
 *               - signature:
 *                   references:
 *                     - by: BODY
 *                     - by: TIMESTAMP
 *                     - xpath: //*[local-name()='order']
 *               - encrypt:
 *                   recipientAlias: backend
 *                   references:
 *                     - by: BODY
 * </code></pre>
 * <p>The placement in that example is the lesson, not a detail: <code>encrypt</code> comes last so the
 * body is signed before it is encrypted, and <code>decrypt</code> comes first so the receiving side
 * undoes that in reverse. Swap either and the signature is computed over one form of the body and
 * checked against another.</p>
 */
@MCElement(name = "wsSecurity")
public class WsSecurityInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsSecurityInterceptor.class);

    private String actor;
    private boolean mustUnderstand = true;
    private KeyStore keyStore;
    private TrustStore trustStore;
    private XmlConfig xmlConfig;
    private ValidateGroup validate;
    private SecureGroup secure;

    @Override
    public void init() {
        super.init();
        if (getValidateParts().isEmpty() && getSecureParts().isEmpty()) {
            throw new ConfigurationException("wsSecurity requires at least one <validate> or <secure> part.");
        }
        checkSecureOrder();
        getValidateParts().forEach(part -> part.init(this));
        getSecureParts().forEach(part -> part.init(this));
    }

    /**
     * The element names of the {@code secure} parts that create what a {@code signature} reference can
     * name, so that "listed before" can be checked for each.
     */
    private static final Map<SignatureReference.By, Class<? extends SecurePart>> SIGNATURE_CREATED_BY = Map.of(
            SignatureReference.By.TIMESTAMP, TimestampSecurePart.class,
            SignatureReference.By.USERNAME_TOKEN, UsernameTokenSecurePart.class,
            SignatureReference.By.ENCRYPTED_KEY, EncryptSecurePart.class);

    /** The same, for what an {@code encrypt} reference can name. */
    private static final Map<EncryptionReference.By, Class<? extends SecurePart>> ENCRYPT_CREATED_BY = Map.of(
            EncryptionReference.By.USERNAME_TOKEN, UsernameTokenSecurePart.class);

    /**
     * WS-SecurityPolicy sanctions both sign-before-encrypt and encrypt-before-sign, and a receiver
     * has to mirror whatever the sender did, so the processing order is the configured order rather
     * than a fixed one. What is checkable up front is that a part cannot cover something that does
     * not exist yet: a <code>signature</code> covering the <code>TIMESTAMP</code> or the
     * <code>USERNAME_TOKEN</code> has to be listed after the part that creates it, or it would
     * silently under-cover the message.
     * <p>
     * Only the relative order is a configuration error. A <code>signature</code> referencing
     * <code>TIMESTAMP</code> without any <code>timestamp</code> part is legitimate - it covers a
     * <code>wsu:Timestamp</code> the message already carried - and is reported at runtime if that
     * turns out to be absent.
     */
    private void checkSecureOrder() {
        List<SecurePart> parts = getSecureParts();
        SIGNATURE_CREATED_BY.forEach((by, creator) -> requireCreatorListedFirst(parts, creator, "signature", by.name(),
                "sign", part -> part instanceof SignatureSecurePart signature && signature.references(by)));
        ENCRYPT_CREATED_BY.forEach((by, creator) -> requireCreatorListedFirst(parts, creator, "encrypt", by.name(),
                "encrypt", part -> part instanceof EncryptSecurePart encrypt && encrypt.references(by)));
        checkNothingCoversWhatEncryptRemoves(parts);
    }

    private static void requireCreatorListedFirst(List<SecurePart> parts, Class<? extends SecurePart> creator,
                                                  String referencingElement, String by, String verb,
                                                  Predicate<SecurePart> references) {
        int creatorIndex = indexOf(parts, creator);
        if (creatorIndex < 0) {
            return;
        }
        for (SecurePart part : parts.subList(0, creatorIndex)) {
            if (references.test(part)) {
                throw new ConfigurationException(
                        ("wsSecurity: a secure/%s referencing by: %s must be listed after the " +
                         "secure part that creates it, otherwise there is nothing there to %s.")
                                .formatted(referencingElement, by, verb));
            }
        }
    }

    /**
     * The mirror image of {@link #requireCreatorListedFirst}: a part can also be listed too <i>late</i>,
     * because an {@code encrypt} with {@code type: ELEMENT} replaces its target outright and a later
     * part naming that element then finds nothing there.
     * <p>
     * A {@code signature} is not the only part that can be listed too late: a second
     * {@code encrypt} naming the same token has the same problem, and would otherwise pass
     * {@code init()} only to fail per message once the token is not where the reference says.
     * <p>
     * Only {@code ELEMENT} destroys anything. {@code CONTENT} leaves the element and its
     * {@code wsu:Id} in place, which is exactly what keeps sign-then-encrypt of the body legal - the
     * common case, and one this must not reject. XPath targets are out of scope, since two
     * expressions cannot be compared statically.
     */
    private static void checkNothingCoversWhatEncryptRemoves(List<SecurePart> parts) {
        for (int i = 0; i < parts.size(); i++) {
            if (!(parts.get(i) instanceof EncryptSecurePart encrypt)
                || !encrypt.replacesElement(EncryptionReference.By.USERNAME_TOKEN)) {
                continue;
            }
            for (SecurePart later : parts.subList(i + 1, parts.size())) {
                if (later instanceof SignatureSecurePart signature
                    && signature.references(SignatureReference.By.USERNAME_TOKEN)) {
                    throw tooLateForTheUsernameToken("signature", "sign");
                }
                if (later instanceof EncryptSecurePart laterEncrypt
                    && laterEncrypt.references(EncryptionReference.By.USERNAME_TOKEN)) {
                    throw tooLateForTheUsernameToken("encrypt", "encrypt");
                }
            }
        }
    }

    private static ConfigurationException tooLateForTheUsernameToken(String element, String verb) {
        return new ConfigurationException(
                ("wsSecurity: a secure/%s referencing by: USERNAME_TOKEN must be listed before the secure/encrypt " +
                 "that replaces it with an xenc:EncryptedData, otherwise there is nothing left there to %s.")
                        .formatted(element, verb));
    }

    private static int indexOf(List<SecurePart> parts, Class<? extends SecurePart> type) {
        for (int i = 0; i < parts.size(); i++) {
            if (type.isInstance(parts.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handle(exc, Flow.REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handle(exc, Flow.RESPONSE);
    }

    private Outcome handle(Exchange exc, Flow flow) {
        Message msg = getMessage(exc, flow);
        if (!SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), msg).isSOAP()) {
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Not a SOAP message.")
                    .detail("The %s body is not XML or does not contain a SOAP body, so WS-Security could not be applied."
                            .formatted(flow.name().toLowerCase()))
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        // Parsing is what can still fail here: the sniff above is the lenient one, so it passes
        // bodies that strict parsing rejects. Everything the document itself causes is answered
        // inside handleParsed, so an exception escaping the read is a malformed body - and that one
        // cannot be answered with a soap:Fault, because an unparseable body does not tell us which
        // envelope version the fault would have to use.
        try {
            return XmlDomBody.read(msg, doc -> handleParsed(exc, flow, msg, doc));
        } catch (Exception e) {
            log.info("Could not parse the {} body as XML: {}", flow.name().toLowerCase(), e.getMessage());
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Not a SOAP message.")
                    .detail("The %s body could not be parsed as XML, so WS-Security could not be applied."
                            .formatted(flow.name().toLowerCase()))
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Outcome handleParsed(Exchange exc, Flow flow, Message msg, Document doc) {
        Element envelope = doc.getDocumentElement();
        String soapNs = envelope.getNamespaceURI();
        try {
            process(exc, flow, doc, envelope, soapNs);
        } catch (WsSecurityFaultException e) {
            log.info("WS-Security check failed: wsse:{}: {}", e.getCode().getLocalName(), e.getMessage());
            exc.setResponse(SoapFaultUtil.create(soapNs, e.getCode(), e.getMessage(),
                    router.getConfiguration().isProduction()));
            return ABORT;
        } catch (Exception e) {
            log.warn("Could not apply WS-Security.", e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not apply WS-Security to the SOAP message.")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        // Not modify(): the document is published only when the checks passed, an aborted message
        // keeps the body it arrived with.
        XmlDomBody.replaceBody(msg, doc);
        return CONTINUE;
    }

    private void process(Exchange exc, Flow flow, Document doc, Element envelope, String soapNs) throws Exception {
        // Done once here rather than per part: marking an Id mutates the shared document, so every
        // part downstream sees it, and #id dereferencing works for all of them.
        markWsuIdAttributes(envelope);

        Element inbound = findSecurity(envelope, soapNs, actor);

        if (!getValidateParts().isEmpty()) {
            if (inbound == null) {
                throw new WsSecurityFaultException(INVALID_SECURITY, actor == null
                        ? "Message has no wsse:Security header."
                        : "Message has no wsse:Security header targeted at actor \"" + actor + "\".");
            }
            runAll(getValidateParts(), new WsSecurityContext(exc, flow, doc, envelope, soapNs, inbound));
            // The group boundary: this element understood the header, so SOAP requires it to be
            // removed rather than forwarded to a next hop that would have to understand it again.
            stripToRetained(inbound, envelope, false);
        } else if (inbound != null) {
            // Nothing here was checked, but this element still owns the header, and the tokens in it are
            // the peer's claims. Forwarded, they would reach the backend as if this gateway had vouched
            // for them: a UsernameToken the backend authenticates, a signature it trusts, next to the
            // security this element is about to add. So they are dropped.
            stripToRetained(inbound, envelope, true);
        }

        if (!getSecureParts().isEmpty()) {
            Element outbound = getOrCreateSecurity(doc, envelope, soapNs, actor, mustUnderstand);
            runAll(getSecureParts(), new WsSecurityContext(exc, flow, doc, envelope, soapNs, outbound));
        }
    }

    /**
     * Consumes the inbound {@code wsse:Security} header, keeping only what this element must not
     * destroy, and removing the header entirely when that leaves nothing.
     * <p>
     * An allowlist rather than a list of the token names to drop: every child of a
     * {@code wsse:Security} header is security content by definition, so anything this element did not
     * check is something it cannot forward. What survives is the children that <i>assert</i> nothing
     * on their own:
     * <ul>
     * <li>{@code wsu:Timestamp}, when the header was never validated ({@code unvalidated}), because
     * a {@code secure/signature} may reference it ({@code by: TIMESTAMP}) to cover a freshness window
     * the message already carried.</li>
     * <li>An {@code xenc:EncryptedKey}, whenever ciphertext it might unlock is still in the message.
     * It is key material addressed to a named recipient, not a claim: if that recipient is a backend
     * rather than this gateway, dropping the key while forwarding the {@code xenc:EncryptedData} in
     * the body - which is not in this header and survives regardless - would turn a valid message into
     * one nobody can ever read. The ciphertext condition makes this self-limiting: once a
     * {@code validate/decrypt} has run nothing is encrypted any more, so a spent key is dropped like
     * anything else.</li>
     * <li>A header {@code xenc:EncryptedData} - an element-encrypted token - but <i>only</i> while a
     * key is being retained alongside it, since it is then one of the things that key unlocks and
     * dropping it would strand the {@code xenc:DataReference} naming it. Never on its own: a piece of
     * ciphertext must not be its own reason to survive, or an unreadable claim would be forwarded
     * forever.</li>
     * <li>A {@code wsse:BinarySecurityToken} directly referenced by a retained key's
     * {@code ds:KeyInfo/wsse:SecurityTokenReference}, so the downstream recipient can still
     * resolve the certificate identifying its decryption key.</li>
     * </ul>
     * Retaining key material is a compatibility accommodation, not a WS-Security requirement. A sender
     * that targets each header at the {@code actor} meant to process it never reaches this path at all,
     * because a header addressed elsewhere is not this element's to consume.
     */
    private static void stripToRetained(Element security, Element envelope, boolean unvalidated) {
        // The two decisions are linked, and in this order: the key survives because ciphertext does,
        // and the header's own ciphertext survives because the key does - never the other way round.
        boolean retainKeyMaterial = getFirstChildByName(security, XENC_NS, "EncryptedKey") != null
                                    && carriesEncryptedData(envelope);
        Set<String> retainedTokenIds = retainKeyMaterial ? referencedEncryptionTokenIds(security) : Set.of();
        for (Element child : childElementsOf(security)) {
            if (unvalidated && WSU_NS.equals(child.getNamespaceURI()) && "Timestamp".equals(child.getLocalName())) {
                continue;
            }
            if (retainKeyMaterial && (isEncryptionMaterial(child)
                    || WSSE_NS.equals(child.getNamespaceURI()) && "BinarySecurityToken".equals(child.getLocalName())
                       && retainedTokenIds.contains(idOf(child)))) {
                log.info("Keeping an inbound {} in the wsse:Security header: the message still carries " +
                         "encrypted content, so the key material is a downstream recipient's to use.",
                        child.getNodeName());
                continue;
            }
            if (unvalidated) {
                log.info("Discarding an unvalidated {} from the inbound wsse:Security header: this " +
                         "wsSecurity element owns the header but has no <validate> list.", child.getNodeName());
            } else {
                // The validated path drops the whole header by design (SOAP's "understood, so not
                // forwarded"), which is unremarkable - at info it would narrate every good message.
                log.debug("Removing {} from the consumed wsse:Security header.", child.getNodeName());
            }
            security.removeChild(child);
        }
        if (childElementsOf(security).isEmpty()) {
            security.getParentNode().removeChild(security);
        }
    }

    private static boolean isEncryptionMaterial(Element child) {
        return XENC_NS.equals(child.getNamespaceURI())
               && ("EncryptedKey".equals(child.getLocalName()) || "EncryptedData".equals(child.getLocalName()));
    }

    private static Set<String> referencedEncryptionTokenIds(Element security) {
        Set<String> ids = new HashSet<>();
        for (Element key : getChildrenByName(security, XENC_NS, "EncryptedKey")) {
            for (Element keyInfo : getChildrenByName(key, DS_NS, "KeyInfo")) {
                for (Element tokenReference : getChildrenByName(keyInfo, WSSE_NS, "SecurityTokenReference")) {
                    for (Element reference : getChildrenByName(tokenReference, WSSE_NS, "Reference")) {
                        String uri = reference.getAttribute("URI");
                        if (uri.startsWith("#") && uri.length() > 1) {
                            ids.add(uri.substring(1));
                        }
                    }
                }
            }
        }
        return ids;
    }

    /** Whether any {@code xenc:EncryptedData} remains anywhere in the message. */
    private static boolean carriesEncryptedData(Element envelope) {
        boolean[] found = {false};
        forEachDescendantElement(envelope, element -> {
            if (XENC_NS.equals(element.getNamespaceURI()) && "EncryptedData".equals(element.getLocalName())) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static void runAll(List<? extends WsSecurityPart> parts, WsSecurityContext ctx) throws Exception {
        for (WsSecurityPart part : parts) {
            part.process(ctx);
        }
    }

    /**
     * The directory a relative keystore/truststore location resolves against. Exposed for the parts,
     * which load those stores on this element's behalf.
     */
    String beanBaseLocation() {
        return getBeanBaseLocation();
    }

    List<ValidatePart> getValidateParts() {
        return validate == null ? List.of() : validate.getValidateParts();
    }

    List<SecurePart> getSecureParts() {
        return secure == null ? List.of() : secure.getSecureParts();
    }

    public String getActor() {
        return actor;
    }

    /**
     * @description The SOAP actor (SOAP 1.1) or role (SOAP 1.2) whose <code>wsse:Security</code>
     * header this element owns. When omitted, that is the header addressed to the ultimate receiver,
     * i.e. the one carrying no <code>actor</code>/<code>role</code> attribute. Headers belonging to
     * any other actor are neither validated nor removed.
     * @example http://example.com/gateway
     */
    @MCAttribute
    public void setActor(String actor) {
        this.actor = actor;
    }

    public boolean isMustUnderstand() {
        return mustUnderstand;
    }

    /**
     * @description Whether the <code>wsse:Security</code> header created by <code>secure</code>
     * carries <code>mustUnderstand</code>, which obliges the next hop to either process the header
     * or answer with a fault instead of ignoring it.
     * @default true
     */
    @MCAttribute
    public void setMustUnderstand(boolean mustUnderstand) {
        this.mustUnderstand = mustUnderstand;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * @description The keystore holding this gateway's own private key: the <code>secure</code> parts
     * that sign use it to sign, and <code>validate</code>/<code>decrypt</code> uses it to decrypt what
     * a peer encrypted for this gateway. Required only when one of those is configured.
     */
    @MCChildElement(order = 1)
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public TrustStore getTrustStore() {
        return trustStore;
    }

    /**
     * @description The truststore holding other parties' certificates: the <code>validate</code> parts
     * that verify a signature check the signing certificate's chain of trust against it, and
     * <code>secure</code>/<code>encrypt</code> resolves its <code>recipientAlias</code> in it to find
     * the certificate to encrypt for. Required only when one of those is configured.
     */
    @MCChildElement(order = 2)
    public void setTrustStore(TrustStore trustStore) {
        this.trustStore = trustStore;
    }

    public XmlConfig getXmlConfig() {
        return xmlConfig;
    }

    /**
     * @description Declares additional XML namespace prefixes usable in the <code>xpath</code>
     * attribute of an <code>XPATH</code> reference, in any part. <code>soap</code>,
     * <code>wsse</code>, and <code>wsu</code> are always available, even when this is set.
     */
    @MCChildElement(allowForeign = true, order = 3)
    public void setXmlConfig(XmlConfig xmlConfig) {
        this.xmlConfig = xmlConfig;
    }

    public ValidateGroup getValidate() {
        return validate;
    }

    /**
     * @description The checks applied to the inbound <code>wsse:Security</code> header, in order.
     */
    @MCChildElement(order = 4)
    public void setValidate(ValidateGroup validate) {
        this.validate = validate;
    }

    public SecureGroup getSecure() {
        return secure;
    }

    /**
     * @description The security applied to the outbound message, in order.
     */
    @MCChildElement(order = 5)
    public void setSecure(SecureGroup secure) {
        this.secure = secure;
    }
}
