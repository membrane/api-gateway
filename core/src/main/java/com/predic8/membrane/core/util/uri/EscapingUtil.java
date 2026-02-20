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

package com.predic8.membrane.core.util.uri;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.*;

import java.net.*;
import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.http.MimeType.isJson;
import static com.predic8.membrane.core.util.uri.EscapingUtil.Escaping.JSON;
import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.*;

public class EscapingUtil {

    /**
     * Specifies the types of escaping that can be performed on strings.
     * <p/>
     * The escaping strategies include:
     * <p/>
     * - {@code NONE}: No escaping is applied. Strings are returned as-is.
     * - {@code URL}: Encodes strings for safe inclusion in a URL, replacing spaces and
     *   other special characters with their percent-encoded counterparts (e.g., SPACE -> +).
     * - {@code SEGMENT}: Encodes strings as safe URI path segments, ensuring they do not introduce
     *   path separators, query delimiters, or other unsafe characters, as per RFC 3986.
     */
    public enum Escaping {
        NONE,
        URL,
        SEGMENT,
        JSON
    }

    public static Optional<Function<String, String>> getEscapingFunction(String mimeType) {
        if (isJson(mimeType)) {
            return Optional.of( getEscapingFunction(JSON));
        }
        return Optional.empty();
    }

    public static Function<String, String> getEscapingFunction(Escaping escaping) {
        return switch (escaping) {
            case NONE -> Function.identity();
            case URL -> s -> URLEncoder.encode(s, UTF_8);
            case SEGMENT -> EscapingUtil::pathEncode;
            case JSON -> CommonBuiltInFunctions::toJSON;
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
