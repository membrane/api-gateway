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
 * What {@link XMLProtector} enforces on a single XML document: the three size limits, and whether a
 * DTD is removed from the document or makes it fail.
 *
 * <p>{@link #UNLIMITED} switches a limit off. Any negative value is normalised to it, so a
 * configured {@code -1} and a configured {@code -5} behave alike.</p>
 */
public record XMLLimits(int maxElementNameLength, int maxAttributeCount, int maxDepth, boolean removeDTD) {

    public static final int UNLIMITED = -1;

    public XMLLimits {
        maxElementNameLength = normalize(maxElementNameLength);
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
}
