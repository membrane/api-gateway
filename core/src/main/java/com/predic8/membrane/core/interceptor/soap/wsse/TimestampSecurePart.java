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
import com.predic8.membrane.core.util.ConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.getChildrenByName;

/**
 * @description Adds a <code>wsu:Timestamp</code> (<code>Created</code>/<code>Expires</code>) to the
 * <code>wsse:Security</code> header, giving a signature a freshness window to defend against
 * message replay. On its own it defends against nothing: list it before a <code>signature</code>
 * that references it with <code>by: TIMESTAMP</code>, so the window itself is covered and cannot be
 * rewritten in transit.
 */
@MCElement(name = "timestamp", component = false, id = "wsSecurity-timestamp")
public class TimestampSecurePart extends SecurePart {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private Duration ttl = DEFAULT_TTL;

    @Override
    void process(WsSecurityContext ctx) {
        Element security = ctx.security();
        // WS-Security allows only one wsu:Timestamp per header, so an existing one is replaced rather
        // than joined by a second. The header is always a fresh one, so this only matters when a
        // configuration lists two timestamp parts - the last one wins.
        for (Element existing : getChildrenByName(security, WSU_NS, "Timestamp")) {
            security.removeChild(existing);
        }
        // wsu:Timestamp is defined to be the first child of wsse:Security, so a receiver can
        // establish freshness before spending work on the rest of the header.
        security.insertBefore(createTimestamp(ctx.document()), security.getFirstChild());
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
     * <code>Created</code>, as an ISO-8601 duration. A validating <code>signature</code> with a
     * required <code>TIMESTAMP</code> reference rejects messages outside this window.
     * @default PT5M
     */
    @MCAttribute
    public void setTtl(String ttl) {
        Duration parsed;
        try {
            parsed = Duration.parse(ttl);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException("ttl \"" + ttl +
                    "\" is not a valid ISO-8601 duration. Use time-based units (days, hours, minutes, " +
                    "seconds), e.g. \"PT5M\" for 5 minutes; calendar units like months are not supported.", e);
        }
        if (!parsed.isPositive()) {
            throw new ConfigurationException("ttl must be a positive duration.");
        }
        this.ttl = parsed;
    }
}
