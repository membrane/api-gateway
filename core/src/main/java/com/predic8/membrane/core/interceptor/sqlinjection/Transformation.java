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

package com.predic8.membrane.core.interceptor.sqlinjection;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The subset of ModSecurity transformations used by the OWASP CRS REQUEST-942
 * rules. CRS regexes assume their input has been normalised by these
 * transformations first; running a regex against raw input would otherwise miss
 * encoded or comment-obfuscated payloads.
 *
 * @see <a href="https://github.com/coreruleset/coreruleset">OWASP CRS</a> (Apache-2.0)
 */
public enum Transformation {

    /** Decode {@code %XX} and the Microsoft {@code %uXXXX} form, and {@code +} to space. */
    urlDecodeUni {
        @Override
        String apply(String s) {
            var out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); ) {
                char c = s.charAt(i);
                if (c == '+') {
                    out.append(' ');
                    i++;
                } else if (c == '%' && i + 1 < s.length() && (s.charAt(i + 1) == 'u' || s.charAt(i + 1) == 'U')) {
                    Integer cp = hex(s, i + 2, 4);
                    if (cp != null) {
                        out.append((char) (int) cp);
                        i += 6;
                    } else {
                        out.append(c);
                        i++;
                    }
                } else if (c == '%') {
                    Integer b = hex(s, i + 1, 2);
                    if (b != null) {
                        out.append((char) (int) b);
                        i += 3;
                    } else {
                        out.append(c);
                        i++;
                    }
                } else {
                    out.append(c);
                    i++;
                }
            }
            return out.toString();
        }
    },

    /** Reinterpret a Latin-1 byte view of the string as UTF-8 (best effort). */
    utf8toUnicode {
        @Override
        String apply(String s) {
            for (int i = 0; i < s.length(); i++)
                if (s.charAt(i) > 0xFF)
                    return s; // already contains non-byte chars; nothing to fold
            byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    },

    /** Replace each C-style comment {@code /* ... *}{@code /} (terminated or not) with a space. */
    replaceComments {
        private final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        private final Pattern UNTERMINATED = Pattern.compile("/\\*.*", Pattern.DOTALL);

        @Override
        String apply(String s) {
            return UNTERMINATED.matcher(COMMENT.matcher(s).replaceAll(" ")).replaceAll(" ");
        }
    },

    /** Remove the comment delimiters {@code /*}, {@code *}{@code /}, {@code --} and {@code #}. */
    removeCommentsChar {
        private final Pattern CHARS = Pattern.compile("/\\*|\\*/|--|#");

        @Override
        String apply(String s) {
            return CHARS.matcher(s).replaceAll("");
        }
    },

    /** Remove all whitespace (including the vertical tab {@code \\x0b} and NBSP). */
    removeWhitespace {
        private final Pattern WS = Pattern.compile("[\\s\\x0b\\u00a0]");

        @Override
        String apply(String s) {
            return WS.matcher(s).replaceAll("");
        }
    };

    abstract String apply(String s);

    private static Integer hex(String s, int from, int len) {
        if (from + len > s.length())
            return null;
        int v = 0;
        for (int i = from; i < from + len; i++) {
            int d = Character.digit(s.charAt(i), 16);
            if (d < 0)
                return null;
            v = (v << 4) | d;
        }
        return v;
    }

    public static String applyAll(String value, List<Transformation> transforms) {
        String s = value;
        for (Transformation t : transforms)
            s = t.apply(s);
        return s;
    }
}
