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

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.apache.commons.io.ByteOrderMark.*;

public class XMLEncodingUtil {

    /**
     * The byte order marks an XML parser detects on its own, and therefore the only ones Membrane
     * lets override a declared charset. UTF-32 is left out deliberately: the parsers in use do not
     * decode it, so detecting it would only replace a declared charset with a failure.
     */
    public static final ByteOrderMark[] XML_BYTE_ORDER_MARKS = {UTF_8, UTF_16BE, UTF_16LE};

    /**
     * The byte order mark {@code bytes} starts with, or null. A BOM identifies the byte stream's
     * encoding directly, so it takes precedence over both a declared charset and the XML
     * declaration - see {@link XMLInputSourceUtil#getInputSource(java.io.InputStream, String)},
     * which applies the same rule to the streaming path.
     */
    public static ByteOrderMark getByteOrderMark(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (BOMInputStream in = BOMInputStream.builder()
                .setInputStream(new ByteArrayInputStream(bytes))
                .setByteOrderMarks(XML_BYTE_ORDER_MARKS)
                .get()) {
            return in.getBOM();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // Cannot happen: the source is a byte array.
        }
    }

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

        ByteOrderMark bom = getByteOrderMark(bytes);
        int offset = bom != null ? bom.length() : 0;

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
