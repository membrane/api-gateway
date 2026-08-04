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
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.SOAPUtil;
import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.getFirstChildByName;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.getOrCreateHeader;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.getOrCreateSecurity;

/**
 * @description Adds a WS-Security <code>wsu:Timestamp</code> (<code>Created</code>/<code>Expires</code>)
 * to the SOAP request's <code>wsse:Security</code> header, giving downstream signing/verification a
 * freshness window to defend against message replay. Place before <code>digitalSignature</code> in
 * the flow and reference it with <code>by: TIMESTAMP</code> to have it covered by the signature.
 * @topic 3. Security
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - wsuTimestamp:
 *         ttl: PT5M
 *     - digitalSignature:
 *         keystore:
 *           location: signing.p12
 *           password: secret
 *         references:
 *           - by: TIMESTAMP
 * </code></pre>
 */
@MCElement(name = "wsuTimestamp")
public class WsuTimestampInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsuTimestampInterceptor.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private Duration ttl = DEFAULT_TTL;

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), exc.getRequest()).isSOAP()) {
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Not a SOAP message.")
                    .detail("Request body is not XML or does not contain a SOAP body, so no wsu:Timestamp could be added.")
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        try {
            Document doc = HardenedXmlParser.getInstance().parse(XMLUtil.getInputSource(exc.getRequest()));

            Element envelope = doc.getDocumentElement();
            String soapNs = envelope.getNamespaceURI();

            Element header = getOrCreateHeader(doc, envelope, soapNs);
            Element security = getOrCreateSecurity(doc, header);
            Element existingTimestamp = getFirstChildByName(security, WSU_NS, "Timestamp");
            if (existingTimestamp != null) {
                security.removeChild(existingTimestamp);
            }
            security.insertBefore(createTimestamp(doc), security.getFirstChild());

            exc.getRequest().setBodyContent(XMLUtil.xmlNode2String(doc).getBytes(StandardCharsets.UTF_8));
            return CONTINUE;
        } catch (Exception e) {
            log.warn("Could not add wsu:Timestamp to SOAP body", e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not add wsu:Timestamp to SOAP body.")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Element createTimestamp(Document doc) {
        Instant now = Instant.now();
        Element timestamp = doc.createElementNS(WSU_NS, "wsu:Timestamp");

        Element created = doc.createElementNS(WSU_NS, "wsu:Created");
        created.setTextContent(now.toString());
        timestamp.appendChild(created);

        Element expires = doc.createElementNS(WSU_NS, "wsu:Expires");
        expires.setTextContent(now.plus(ttl).toString());
        timestamp.appendChild(expires);

        return timestamp;
    }

    public String getTtl() {
        return ttl.toString();
    }

    /**
     * @description How long the timestamp stays fresh, i.e. <code>Expires</code> minus
     * <code>Created</code>, as an ISO-8601 duration. A <code>digitalSignatureVerifier</code>
     * checking a required <code>TIMESTAMP</code> reference rejects requests outside this window.
     * @default PT5M
     */
    @MCAttribute
    public void setTtl(String ttl) {
        Duration parsed = Duration.parse(ttl);
        if (!parsed.isPositive()) {
            throw new ConfigurationException("ttl must be a positive duration.");
        }
        this.ttl = parsed;
    }
}
