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

package com.predic8.membrane.core.interceptor.xmlprotection;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

/**
 * The outcome of scanning one XML document. A rejection carries the reason it was rejected for, so
 * that every violation - a limit that was exceeded, a DOCTYPE pointing outside the document, or XML
 * that is not well-formed - travels back to the caller on one channel.
 *
 * <p>{@link Accepted} and {@link Rewritten} both let the document pass; they differ in which copy of
 * it the caller has to forward.</p>
 */
public sealed interface XMLProtectionResult {

    /** The document is within the configured limits and nothing had to be taken out of it. */
    XMLProtectionResult ACCEPTED = new Accepted();

    /** The document is within the configured limits, but its DTD was dropped on the way through. */
    XMLProtectionResult REWRITTEN = new Rewritten();

    /**
     * The document may be passed on as it arrived. What the writer produced is an equivalent
     * document, not a safer one, so the caller can keep the original bytes rather than forward a
     * re-serialised copy of them.
     */
    record Accepted() implements XMLProtectionResult {
    }

    /**
     * The document may be passed on, but only as the writer rewrote it: the original still carries
     * the DTD that was removed.
     */
    record Rewritten() implements XMLProtectionResult {
    }

    /**
     * The document violates the policy and must not be passed on. The document written so far is
     * incomplete and has to be discarded.
     *
     * @param reason what was violated, for the log and for the error response outside production
     */
    record Rejected(String reason) implements XMLProtectionResult {

        /**
         * A rejection that names where in the document the parser stopped. A
         * {@link XMLStreamException} need not carry a {@link Location}, so line and column are
         * reported as {@code -1} when it does not.
         *
         * @param what what was wrong, put in front of the parser's own message
         */
        static Rejected at(String what, XMLStreamException e) {
            Location loc = e.getLocation();
            return new Rejected("%s at line %d, column %d: %s".formatted(what,
                    loc != null ? loc.getLineNumber() : -1,
                    loc != null ? loc.getColumnNumber() : -1,
                    e.getMessage()));
        }
    }
}
