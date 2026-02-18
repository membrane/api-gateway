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

package com.predic8.membrane.core.util;

public class URIValidationUtil {

    public static void validateDigits(String port) {
        for (int i = 0; i < port.length(); i++) {
            if (!isDigit(port.charAt(i)))
                throw new IllegalArgumentException("Invalid port: " + port);
        }
    }

    public static boolean isPchar(char c) {
        // pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
        return isUnreserved(c) || isSubDelims(c) || c == ':' || c == '@';
    }

    public static boolean isUnreserved(char c) {
        return isAlpha(c) || isDigit(c) || c == '-' || c == '.' || c == '_' || c == '~';
    }

    public static boolean isSubDelims(char c) {
        return c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')' ||
               c == '*' || c == '+' || c == ',' || c == ';' || c == '=';
    }

    public static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    public static boolean isDigit(char c) {
        return (c >= '0' && c <= '9');
    }

    public static boolean isHex(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'A' && c <= 'F') ||
               (c >= 'a' && c <= 'f');
    }

    /**
     * Security focused validation only: host may be a reg-name or IPv4-ish or contain IPv6 literals.
     * Does not validate correctness of IP addresses. Only enforces allowed characters.
     * <p/>
     * Allowed: unreserved, sub-delims, '.', '%', ':', '[', ']'.
     */
    public static void validateHost(String s) {
        if (s == null || s.isEmpty())
            throw new IllegalArgumentException("Host must not be empty.");

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '.' || c == '%' || c == ':' || c == '[' || c == ']')
                continue;

            if (isUnreserved(c) || isSubDelims(c))
                continue;

            throw new IllegalArgumentException("Invalid character in host: '" + c + "'");
        }
    }
}
