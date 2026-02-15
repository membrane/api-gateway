/* Copyright 2013 predic8 GmbH, www.predic8.com

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

import java.net.*;

import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.*;

public class URLUtil {

    public static String getHost(String uri) {
        int i = uri.indexOf(":") + 1;
        while (uri.charAt(i) == '/')
            i++;
        int j = uri.indexOf("/", i);
        return j == -1 ? uri.substring(i) : uri.substring(i, j);
    }

    public static String getPathQuery(URIFactory uriFactory, String uri) throws URISyntaxException {
        URI u = uriFactory.create(uri);
        String query = u.getRawQuery();
        String path = u.getRawPath();
        return (path.isEmpty() ? "/" : path) + (query == null ? "" : "?" + query);
    }

    /**
     * Extracts and returns the name component from the path of a URI. The name
     * corresponds to the substring after the last '/' in the path. If no '/' is
     * found, the entire path is returned.
     *
     * @param uriFactory An instance of {@code URIFactory} used to create the {@code URI} object.
     * @param uri        The URI string to process.
     * @return The name component extracted from the URI's path.
     * @throws URISyntaxException If the URI string is invalid and cannot be converted into a {@code URI}.
     */
    public static String getNameComponent(URIFactory uriFactory, String uri) throws URISyntaxException {
        var p = uriFactory.create(uri).getPath();
        int i = p.lastIndexOf('/');
        return i == -1 ? p : p.substring(i + 1);
    }

    public static int getPortFromURL(URL loc2) {
        return loc2.getPort() == -1 ? loc2.getDefaultPort() : loc2.getPort();
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
     * pathSeg("a/b & c")  -> "a%2Fb%20%26%20c"
     * pathSeg("ä")        -> "%C3%A4"
     * pathSeg(123)        -> "123"
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
    public static String pathSeg(Object value) {
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
