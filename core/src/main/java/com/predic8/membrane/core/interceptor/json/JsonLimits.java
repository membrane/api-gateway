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

package com.predic8.membrane.core.interceptor.json;

/**
 * The limits a {@link JsonProtection} enforces on a single JSON document.
 *
 * <p>A key is also a string, so a key length above {@code maxStringLength} could never be reached:
 * the stricter of the two limits applies and is resolved here, once, rather than at every check.</p>
 *
 * <p>{@link #UNLIMITED} switches {@code maxSize} off. Any negative value is normalised to it, so a
 * configured {@code -1} and a configured {@code -5} behave alike.</p>
 */
public record JsonLimits(int maxTokens, int maxSize, int maxDepth, int maxStringLength, int maxKeyLength,
                         int maxObjectSize, int maxArraySize, boolean blockProto) {

    public static final int UNLIMITED = -1;

    public JsonLimits {
        maxKeyLength = Math.min(maxKeyLength, maxStringLength);
        maxSize = maxSize < 0 ? UNLIMITED : maxSize;
    }

    /**
     * @return whether {@code size} is over {@code maxSize}. An {@link #UNLIMITED} limit is never
     * exceeded.
     */
    public boolean exceedsMaxSize(long size) {
        return maxSize != UNLIMITED && size > maxSize;
    }

    /**
     * The same limit as a plain ceiling, for a caller that has no way to express "no limit" - such
     * as the per-part buffer a multipart body is read through, which needs a number of bytes.
     */
    public int sizeCeiling() {
        return maxSize == UNLIMITED ? Integer.MAX_VALUE : maxSize;
    }
}
