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

package com.predic8.membrane.core.util.xml;

import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class XMLEncodingUtil {

    // XML declaration must be ASCII-compatible
    private static final Pattern XML_DECL_PATTERN =
            Pattern.compile("^\\s*<\\?xml\\s+([^?]*?)\\?>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ENCODING_ATTR_PATTERN =
            Pattern.compile("(?i)\\bencoding\\s*=\\s*(['\"])([^'\"]+)\\1");

    /**
     * Extracts encoding from an XML prolog using raw bytes.
     * XML spec guarantees the prolog is ASCII-compatible, so we
     * decode only a small prefix as ISO-8859-1.
     * @param bytes XML document bytes
     * @return encoding name (e.g. UTF-8, ISO-8859-1) or null if absent
     */
    public static String getEncodingFromXMLProlog(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        int offset = 0;

        // UTF-8 BOM
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }

        // XML declaration must appear at the start (after BOM + whitespace)
        int max = Math.min(bytes.length, offset + 1024);

        // ISO-8859-1 preserves byte values 1:1 → safe for ASCII parsing
        String prefix = new String(bytes, offset, max - offset, ISO_8859_1);

        Matcher decl = XML_DECL_PATTERN.matcher(prefix);
        if (!decl.find()) return null;

        String declBody = decl.group(1);
        Matcher enc = ENCODING_ATTR_PATTERN.matcher(declBody);
        if (!enc.find()) return null;

        return enc.group(2).trim().toUpperCase(Locale.ROOT);
    }
}
