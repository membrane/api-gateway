package com.predic8.membrane.core.transport.http.method;

public class DefaultMethodValidator extends AbstractMethodValidator {

    @Override
    protected boolean matches(String method) {
        char first = method.charAt(0);
        if (!isAsciiLetter(first))
            return false;

        for (int i = 1; i < method.length(); i++) {
            char c = method.charAt(i);
            if (!isAsciiLetter(c) && !isDigit(c) && c != '-' && c != '_')
                return false;
        }

        return true;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
