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

package com.predic8.membrane.core.util.text;

import com.fasterxml.jackson.databind.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.text.SerializationFunction.*;
import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.*;

public class SerializationUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private SerializationUtil() {
    }

    /**
     * Specifies the types of serialization that can be performed on strings.
     * <p/>
     * The strategies include:
     * <p/>
     * - {@code URL}: Encodes strings for safe inclusion in a URL, replacing spaces and
     * other special characters with their percent-encoded counterparts (e.g., SPACE -> +).
     * - {@code JSON}: Serializes s for safe inclusion in a JSON context.
     * - {@code XML}: Serializes for safe inclusion in an XML context using XML 1.1 rules.
     * - {@code SEGMENT}: Encodes as safe URI path segments, ensuring they do not introduce
     * - {@code TEXT}: Serializes as plain text, without any encoding.
     * path separators, query delimiters, or other unsafe characters, as per RFC 3986.
     */
    public enum Serialization {
        TEXT,
        URL,
        SEGMENT,
        JSON,
        XML
    }

    public static Optional<SerializationFunction> getSerialization(String mimeType) {
        if (isJson(mimeType)) {
            return Optional.of(JSON_SERIALIZATION);
        }
        if (isXML(mimeType)) {
            return Optional.of(XML_SERIALIZATION);
        }
        if (isText(mimeType)) {
            return Optional.of(TEXT_SERIALIZATION);
        }
        // Optional needed, so the caller can warn that a default is used
        return Optional.empty();
    }

    public static SerializationFunction getSerialization(Serialization escaping) {
        return switch (escaping) {
            case TEXT -> TEXT_SERIALIZATION;
            case URL -> URL_SERIALIZATION;
            case SEGMENT -> SEGMENT_SERIALIZATION;
            case JSON -> JSON_SERIALIZATION;
            case XML -> XML_SERIALIZATION;
        };
    }

    /**
     * Encodes the given value so it can be safely used as a single URI path segment.
     *
     * <p>The method performs percent-encoding according to RFC&nbsp;3986 for
     * <em>path segment</em> context. All characters except the unreserved set
     * {@code A-Z a-z 0-9 - . _ ~} are UTF-8 encoded and emitted as {@code %HH}
     * sequences.</p>
     *
     * <p>This guarantees that the returned string:</p>
     * <ul>
     *   <li>cannot introduce additional path separators ({@code /})</li>
     *   <li>cannot inject query or fragment delimiters ({@code ?, #, &})</li>
     *   <li>does not rely on {@code +} for spaces (spaces become {@code %20})</li>
     *   <li>is safe to concatenate into {@code ".../foo/" + pathSeg(value)}</li>
     * </ul>
     *
     * <p>The input is converted using {@link Object#toString()} and encoded as UTF-8.
     * A {@code null} value results in an empty string.</p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * pathEncode("a/b & c")  -> "a%2Fb%20%26%20c"
     * pathEncode("ä")        -> "%C3%A4"
     * pathEncode(123)        -> "123"
     * }</pre>
     *
     * <p><strong>Note:</strong> This method is intended for encoding a single
     * path segment only. It must not be used for whole URLs, query strings,
     * or already structured paths. For those cases, use a URI builder or
     * context-specific encoding.</p>
     *
     * @param value the value to encode as a path segment; may be {@code null}
     * @return a percent-encoded string safe for use as one URI path segment
     */
    public static String pathEncode(Object value) {
        if (value == null) return "";

        byte[] bytes = value.toString().getBytes(UTF_8);
        var out = new StringBuilder(bytes.length * 3);

        for (byte b : bytes) {
            int c = b & 0xff;

            // RFC 3986 unreserved characters
            if ((c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '.' || c == '_' || c == '~') {

                out.append((char) c);
            } else {
                out.append('%');
                char hex1 = toUpperCase(forDigit((c >> 4) & 0xF, 16));
                char hex2 = toUpperCase(forDigit(c & 0xF, 16));
                out.append(hex1).append(hex2);
            }
        }

        return out.toString();
    }
}