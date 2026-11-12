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

import static com.predic8.membrane.core.util.URIValidationUtil.isAlpha;
import static com.predic8.membrane.core.util.URIValidationUtil.isDigit;

public class URLUtil {

    public static String getAuthority(String uri) {
        int i = uri.indexOf(":") + 1;
        while (uri.charAt(i) == '/')
            i++;
        int j = uri.indexOf("/", i);
        return j == -1 ? uri.substring(i) : uri.substring(i, j);
    }

    /**
     * The scheme and authority at the start of a URL reference, e.g. "https://example.com" of
     * "https://example.com/x", or "" if the reference names no host and stays on the host of the request. Like a
     * browser (WHATWG URL Standard), it takes a backslash for a slash in front of the authority, and finds an
     * authority after any scheme, even without slashes as in "https:example.com". The authority runs up to the first
     * '/', '?' or '#'. A browser also ends it at a backslash, but other clients don't.
     */
    public static String getSchemeAndAuthority(String reference) {
        var i = 0;
        if (isSlash(reference, 0) && isSlash(reference, 1)) {
            i = 2;
        } else {
            i = schemeLength(reference);
            if (i == 0)
                return "";
        }
        while (isSlash(reference, i))
            i++;
        while (i < reference.length() && "/?#".indexOf(reference.charAt(i)) == -1)
            i++;
        return reference.substring(0, i);
    }

    static boolean isSlash(String reference, int index) {
        return index < reference.length() && (reference.charAt(index) == '/' || reference.charAt(index) == '\\');
    }

    /**
     * @return the length of the scheme and ':' at the start of the reference (RFC 3986 section 3.1), or 0 if there is
     * no scheme
     */
    static int schemeLength(String reference) {
        if (reference.isEmpty() || !isAlpha(reference.charAt(0)))
            return 0;
        for (var i = 1; i < reference.length(); i++) {
            final var c = reference.charAt(i);
            if (c == ':')
                return i + 1;
            if (!isAlpha(c) && !isDigit(c) && c != '+' && c != '-' && c != '.')
                return 0;
        }
        return 0;
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

}
