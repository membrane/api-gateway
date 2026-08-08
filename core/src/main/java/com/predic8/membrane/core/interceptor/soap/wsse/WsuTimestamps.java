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

import org.w3c.dom.Element;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;

/**
 * Reading and checking the {@code wsu:Timestamp} of a {@code wsse:Security} header.
 * <p>
 * Shared by {@link TimestampValidatePart}, which checks freshness on its own, and
 * {@link SignatureValidatePart}, which checks it for a required {@code TIMESTAMP} reference - the
 * check has to mean the same thing in both, since the second is what makes the window trustworthy and
 * the first is what makes it enforced.
 */
final class WsuTimestamps {

    private WsuTimestamps() {
    }

    /**
     * The one {@code wsu:Timestamp} of the header.
     *
     * @throws WsSecurityFaultException if the header carries none, or more than one - WS-Security
     *                                  allows only one, and which of two a receiver honoured would
     *                                  otherwise decide whether the message is fresh
     */
    static Element requireSingleTimestamp(Element security) {
        List<Element> timestamps = getChildrenByName(security, WSU_NS, "Timestamp");
        if (timestamps.isEmpty()) {
            throw new WsSecurityFaultException(INVALID_SECURITY, "wsse:Security carries no wsu:Timestamp.");
        }
        if (timestamps.size() > 1) {
            throw new WsSecurityFaultException(INVALID_SECURITY,
                    "More than one wsu:Timestamp found; rejecting as ambiguous.");
        }
        return timestamps.getFirst();
    }

    /**
     * Checks that the timestamp's window covers now, give or take {@code clockSkew}.
     * <p>
     * {@code Created} is checked as well as {@code Expires}, not instead of it: that is what catches a
     * stale timestamp carrying no {@code Expires} at all, which is legal and would otherwise never go
     * out of date.
     */
    static void checkFreshness(Element timestamp, Duration clockSkew) {
        Instant created = parse(timestamp, "Created");
        Instant expires = parse(timestamp, "Expires");
        Instant now = Instant.now();
        if (created.isAfter(now.plus(clockSkew))) {
            // Not expired - the sender's clock is ahead, or the value was made up.
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "wsu:Timestamp Created (" + created + ") lies in the future.");
        }
        if (created.isBefore(now.minus(clockSkew))) {
            throw new WsSecurityFaultException(MESSAGE_EXPIRED,
                    "wsu:Timestamp Created (" + created + ") is older than the allowed clock skew.");
        }
        if (expires != null && expires.isBefore(now.minus(clockSkew))) {
            throw new WsSecurityFaultException(MESSAGE_EXPIRED,
                    "wsu:Timestamp has expired (Expires=" + expires + ").");
        }
    }

    /**
     * @return the named child's instant, or null when the child is absent and optional
     * @throws WsSecurityFaultException if {@code Created} is absent, or either value is not an
     *                                  {@code xs:dateTime}
     */
    private static Instant parse(Element timestamp, String localName) {
        Element el = getFirstChildByName(timestamp, WSU_NS, localName);
        if (el == null) {
            if ("Created".equals(localName)) {
                throw new WsSecurityFaultException(FAILED_CHECK, "wsu:Timestamp has no wsu:Created.");
            }
            return null;
        }
        try {
            // OffsetDateTime rather than Instant: xs:dateTime permits any offset, not only "Z".
            return OffsetDateTime.parse(el.getTextContent().trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw new WsSecurityFaultException(FAILED_CHECK,
                    "wsu:Timestamp/wsu:" + localName + " is not a valid xs:dateTime: " + el.getTextContent());
        }
    }
}
