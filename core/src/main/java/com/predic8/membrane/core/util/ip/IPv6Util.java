package com.predic8.membrane.core.util.ip;

import com.predic8.membrane.core.util.*;

public class IPv6Util {

    /**
     * Security focused validation only: allowed characters for an IPv6 address text.
     * Does not validate IPv6 semantics. Intended for bracket hosts like "[...]" where ':' is expected.
     *
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
