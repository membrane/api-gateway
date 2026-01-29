/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.text;

import java.util.*;

import static java.lang.Math.min;

public class StringUtil {

    /**
     * Truncates a string after a given length.
     * @param s string to truncate
     * @param maxLength maximal length of the string
     * @return truncated string
     */
    public static String truncateAfter(String s, int maxLength) {
        return s.substring(0, min(s.length(), maxLength));
    }

    /**
     * Returns the last `maxLength` characters of the input string.
     * If the input string's length is less than `maxLength`, the entire string is returned.
     *
     * @param s the input string, must not be null
     * @param maxLength the maximum number of characters to extract from the end of the string
     * @return a substring containing the last `maxLength` characters of the input string
     */
    public static String tail(String s, int maxLength) {
        return s.substring(Math.max(s.length() - maxLength,0));
    }

    /**
     * Replaces all non-printable ASCII characters in the input string with an underscore ('_').
     * Printable characters are considered to be those in the range from 32 (space) to 126 (tilde).
     * This method is useful for sanitizing input to make it safe for logging or displaying.
     *
     * @param s the input string to be sanitized
     * @return a new string with non-printable characters replaced by underscores
     */
    public static String maskNonPrintableCharacters(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    /**
     * Adds lines numbers like a code listing
     */
    public static String addLineNumbers(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String line : s.split("\n")) {
            sb.append(String.format("%4d: %s\n", i++, line));
        }
        return sb.toString();
    }

    /**
     * Splits a comma-separated string into a list of trimmed values.
     * Whitespace around commas is ignored.
     *
     * @param s the string to split, may be null
     * @return a list of values; empty list if input is null or empty.
     *         Note: consecutive commas produce empty strings in the result.
     *         The returned list is fixed-size and cannot be modified.
     */
    public static List<String> splitByComma(String s) {
        return s == null || s.isEmpty() ? Collections.emptyList() : Arrays.asList(s.split("\\s*,\\s*"));
    }

    public static boolean yes(String override) {
        if (override == null)
            return false;

        return switch (override.trim().toLowerCase()) {
            case "on", "yes", "y", "true", "1", "enable", "enabled" -> true;
            default -> false;
        };
    }

    public static boolean no(String override) {
        if (override == null)
            return false;

        return switch (override.trim().toLowerCase()) {
            case "off", "no", "n", "false", "0", "disable", "disabled" -> true;
            default -> false;
        };
    }
}
