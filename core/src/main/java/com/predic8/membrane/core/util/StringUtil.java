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

package com.predic8.membrane.core.util;

import static java.lang.Math.min;

public class StringUtil {

    /**
     *
     * @param s
     * @param maxLength
     * @return
     */
    public static String truncateAfter(String s, int maxLength) {
        return s.substring(0, min(s.length(), maxLength));
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
     * @param s
     * @return
     */
    public static String addLineNumbers(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String line : s.split("\n")) {
            sb.append(String.format("%4d: %s\n", i++, line));
        }
        return sb.toString();
    }

}
