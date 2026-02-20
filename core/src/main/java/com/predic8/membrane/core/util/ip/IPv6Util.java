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

package com.predic8.membrane.core.util.ip;

import com.predic8.membrane.core.util.*;

public class IPv6Util {

    /**
     * Security focused validation only: allowed characters for an IPv6 address text.
     * Does not validate IPv6 semantics. Intended for bracket hosts like "[...]" where ':' is expected.
     * <p/>
     * Allowed: HEX, ':', '.', '%', unreserved, sub-delims, '[' and ']'.
     * '%' is allowed because zone IDs and percent-encoded sequences may appear (validation of %HH is done elsewhere).
     */
    public static void validateIP6Address(String s) {
        if (s == null || s.isEmpty())
            throw new IllegalArgumentException("Invalid IPv6 address: empty.");

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (URIValidationUtil.isHex(c) || c == ':' || c == '.' || c == '%' || c == '[' || c == ']')
                continue;

            if (URIValidationUtil.isUnreserved(c) || URIValidationUtil.isSubDelims(c))
                continue;

            throw new IllegalArgumentException("Invalid character in IPv6 address: '" + c + "'");
        }
    }
}
