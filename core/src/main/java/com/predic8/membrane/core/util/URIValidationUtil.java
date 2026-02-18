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
     *
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
