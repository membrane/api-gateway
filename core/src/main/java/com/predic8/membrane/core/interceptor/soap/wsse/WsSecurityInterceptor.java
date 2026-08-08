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

import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.INVALID_SECURITY;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;

/**
 * @description <p>Owns the WS-Security (<code>wsse:Security</code>) header of a SOAP message: the
 * <code>validate</code> list consumes the security the peer sent, the <code>secure</code> list
 * applies fresh security for the next hop. Both lists are optional and run in the order they are
 * written; the whole <code>validate</code> list runs first, and the inbound header is removed at
 * that boundary before <code>secure</code> creates a new one. Header blocks targeted at a different
 * <code>actor</code> are left untouched.</p>
 * <p>Direction is not part of this element: nest it in <code>request</code> or <code>response</code>
 * to say which message it applies to. A gateway commonly validates what a client sent and
 * re-secures for the backend in one element, and mirrors that on the way back. Use two elements
 * with a transformation between them when the body has to change between validating and
 * re-securing.</p>
 * <p>A failing check answers with a <code>soap:Fault</code> matching the envelope version of the
 * offending message, carrying the WS-Security fault code (<code>wsse:FailedAuthentication</code>,
 * <code>wsse:FailedCheck</code>, and so on); a body that is not SOAP at all answers with Problem
 * Details, since no fault envelope can be produced for it.</p>
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
 *               - usernameToken:
 *                   username: ${property.apiUser}
 *                   password: ${property.apiPassword}
 *               - signature:
 *                   requiredReferences:
 *                     - by: BODY
 *                     - by: TIMESTAMP
 *             secure:
 *               - timestamp:
 *                   ttl: PT5M
 *               - signature:
 *                   references:
 *                     - by: BODY
 *                     - by: TIMESTAMP
 *                     - xpath: //*[local-name()='order']
 * </code></pre>
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
     * WS-SecurityPolicy sanctions both sign-before-encrypt and encrypt-before-sign, and a receiver
     * has to mirror whatever the sender did, so the processing order is the configured order rather
     * than a fixed one. What is checkable up front is that a part cannot cover something that does
     * not exist yet: a <code>signature</code> covering the <code>TIMESTAMP</code> has to be listed
     * after the <code>timestamp</code> that creates it, or it would silently under-cover the message.
     * <p>
     * Only the relative order is a configuration error. A <code>signature</code> referencing
     * <code>TIMESTAMP</code> without any <code>timestamp</code> part is legitimate - it covers a
     * <code>wsu:Timestamp</code> the message already carried - and is reported at runtime if that
     * turns out to be absent.
     */
    private void checkSecureOrder() {
        List<SecurePart> parts = getSecureParts();
        int timestampIndex = indexOfTimestamp(parts);
        if (timestampIndex < 0) {
            return;
        }
        for (SecurePart part : parts.subList(0, timestampIndex)) {
            if (part instanceof SignatureSecurePart signature && signature.referencesTimestamp()) {
                throw new ConfigurationException(
                        "wsSecurity: a secure/signature referencing by: TIMESTAMP must be listed after the " +
                        "secure/timestamp that creates it, otherwise there is no wsu:Timestamp to sign.");
            }
        }
    }

    private static int indexOfTimestamp(List<SecurePart> parts) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i) instanceof TimestampSecurePart) {
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

        // Not inside the try below: that one answers with a soap:Fault, and the envelope version a
        // fault would have to use is exactly what an unparseable body does not tell us. The sniff
        // above is the lenient one, so it can pass a body that strict parsing still rejects.
        final Document doc;
        try {
            doc = XmlDomBody.documentOf(msg);
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
        XmlDomBody.replaceBody(msg, doc);
        return CONTINUE;
    }

    private void process(Exchange exc, Flow flow, Document doc, Element envelope, String soapNs) throws Exception {
        // Done once here rather than per part: marking an Id mutates the shared document, so every
        // part downstream sees it, and #id dereferencing works for all of them.
        markWsuIdAttributes(envelope);

        if (!getValidateParts().isEmpty()) {
            Element inbound = findSecurity(envelope, soapNs, actor);
            if (inbound == null) {
                throw new WsSecurityFaultException(INVALID_SECURITY, actor == null
                        ? "Message has no wsse:Security header."
                        : "Message has no wsse:Security header targeted at actor \"" + actor + "\".");
            }
            runAll(getValidateParts(), new WsSecurityContext(exc, flow, doc, envelope, soapNs, inbound));
            // The group boundary: this element understood the header, so SOAP requires it to be
            // removed rather than forwarded to a next hop that would have to understand it again.
            inbound.getParentNode().removeChild(inbound);
        }

        if (!getSecureParts().isEmpty()) {
            Element outbound = getOrCreateSecurity(doc, envelope, soapNs, actor, mustUnderstand);
            runAll(getSecureParts(), new WsSecurityContext(exc, flow, doc, envelope, soapNs, outbound));
        }
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
     * @description The keystore holding the private key and certificate used by the
     * <code>secure</code> parts that sign. Required only when one of them does.
     */
    @MCChildElement(order = 1)
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public TrustStore getTrustStore() {
        return trustStore;
    }

    /**
     * @description The truststore holding the CA certificates used by the <code>validate</code>
     * parts that check a signing certificate's chain of trust. Required only when one of them does.
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
