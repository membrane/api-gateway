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
 * The limits a {@link JsonProtectionScanner} enforces on a single JSON document.
 *
 * <p>A key is also a string, so a key length above {@code maxStringLength} could never be reached:
 * the stricter of the two limits applies and is resolved here, once, rather than at every check.</p>
 */
public record JsonLimits(int maxTokens, int maxSize, int maxDepth, int maxStringLength, int maxKeyLength,
                         int maxObjectSize, int maxArraySize, boolean blockProto) {

    public JsonLimits {
        maxKeyLength = Math.min(maxKeyLength, maxStringLength);
    }
}
