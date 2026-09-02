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

/**
 * What {@link XMLProtector} enforces on a single XML document: the four size limits, and whether a
 * DTD is removed from the document or makes it fail.
 *
 * <p>The two name limits apply to the name as it appears in the document, {@code prefix:localName},
 * because a prefix is as attacker-controlled as the local name is.</p>
 *
 * <p>{@link #UNLIMITED} switches a limit off. Any negative value is normalised to it, so a
 * configured {@code -1} and a configured {@code -5} behave alike.</p>
 */
public record XMLLimits(int maxElementNameLength, int maxAttributeNameLength, int maxAttributeCount,
                        int maxDepth, boolean removeDTD) {

    public static final int UNLIMITED = -1;

    /** What the JAXP name limit reads as "no limit", as opposed to "no name". */
    private static final int NO_JAXP_LIMIT = 0;

    public XMLLimits {
        maxElementNameLength = normalize(maxElementNameLength);
        maxAttributeNameLength = normalize(maxAttributeNameLength);
        maxAttributeCount = normalize(maxAttributeCount);
        maxDepth = normalize(maxDepth);
    }

    private static int normalize(int limit) {
        return limit < 0 ? UNLIMITED : limit;
    }

    /**
     * @return whether {@code value} is over {@code limit}. An {@link #UNLIMITED} limit is never exceeded.
     */
    public static boolean exceeds(int value, int limit) {
        return limit != UNLIMITED && value > limit;
    }

    /**
     * How far the JAXP cap sits above the configured name limits. It has to be a wide margin, not a
     * single character: JAXP aborts a name the moment it grows past the cap, while the checks here
     * only see a name once the element around it has been parsed. A cap just above the limit would
     * therefore win for every name more than one character too long, and report a name violation as
     * "not well-formed" rather than naming it.
     */
    public static final int JAXP_NAME_HEADROOM = 100_000;

    /**
     * The cap for {@code jdk.xml.maxXMLNameLimit}: a coarse backstop, deliberately far above the
     * limits enforced here, for the names these checks never see - namespace prefixes and URIs,
     * processing instruction targets, and entity names. Everything within the headroom is decided by
     * the checks above, which can say what was violated; JAXP only stops names no legitimate
     * document produces.
     *
     * @return the cap, or 0 for none - which is what an {@link #UNLIMITED} name limit has to mean,
     * since JAXP must not cap what the configuration deliberately does not
     */
    public int jaxpNameLimit() {
        if (maxElementNameLength == UNLIMITED || maxAttributeNameLength == UNLIMITED)
            return NO_JAXP_LIMIT;

        int largest = Math.max(maxElementNameLength, maxAttributeNameLength);
        if (largest > Integer.MAX_VALUE - JAXP_NAME_HEADROOM)
            return NO_JAXP_LIMIT;
        return largest + JAXP_NAME_HEADROOM;
    }
}
