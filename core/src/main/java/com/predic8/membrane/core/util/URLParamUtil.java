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

import com.predic8.membrane.core.exchange.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;

public class URLParamUtil {

    private static final Logger log = LoggerFactory.getLogger(URLParamUtil.class);

    private static final Pattern paramsPat = Pattern.compile("([^=]*)=?(.*)");

    public static Map<String, List<String>> getParams(URIFactory uriFactory, Exchange exc) throws URISyntaxException, IOException {
        var uri = exc.getRequest().getUri();

        if (uri == null || uri.isEmpty())
            return emptyMap();

        return parseQueryString(getParametersString(uriFactory,exc,uri));
    }

    public static Map<String, String> getParams(URIFactory uriFactory, Exchange exc, DuplicateKeyOrInvalidFormStrategy duplicateKeyOrInvalidFormStrategy) throws URISyntaxException, IOException {
        var uri = exc.getRequest().getUri();

        if (uri == null || uri.isEmpty())
            return emptyMap();

        return parseQueryString(getParametersString(uriFactory, exc, uri), duplicateKeyOrInvalidFormStrategy);
    }

    /**
     * Retrieves the parameter string from the provided URI or HTTP request body.
     *
     * If the URI contains a query component, the raw query string is returned.
     * Otherwise, if the request does not contain form parameters, it returns null.
     * If form parameters exist, it retrieves and decodes the parameters from the request body.
     *
     * @param uriFactory the factory used to create and manipulate URIs
     * @param exc the HTTP exchange object containing request and response information
     * @param uri the URI string from which parameters may be extracted
     * @return the parameter string from the URI or request body, or null if no parameters are found
     * @throws URISyntaxException if the URI syntax is invalid
     * @throws IOException if an I/O error occurs while reading the request body
     */
    private static String getParametersString(URIFactory uriFactory, Exchange exc, String uri) throws URISyntaxException, IOException {
        var q = getUri(uriFactory, uri).getRawQuery();
        if (q != null)
            return q;

        if (hasNoFormParams(exc))
            return null;

        return exc.getRequest().getBodyAsStringDecoded(); // WWW-Form-URLEncoded parameters are in the body
    }

    private static URI getUri(URIFactory uriFactory, String uri) throws URISyntaxException {
        try {
            return uriFactory.create(uri);
        } catch (URISyntaxException e) {
            log.info("Error parsing query params: {} URI: {}", e.getMessage(), uri);
            throw e;
        }
    }

    public static String getStringParam(URIFactory uriFactory, Exchange exc, String name) throws Exception {
        return getParams(uriFactory, exc, ERROR).get(name);
    }

    public static int getIntParam(URIFactory uriFactory, Exchange exc, String name) throws Exception {
        return Integer.parseInt(getParams(uriFactory, exc, ERROR).get(name));
    }

    public enum DuplicateKeyOrInvalidFormStrategy {
        ERROR,
        MERGE_USING_COMMA
    }

    public static boolean hasNoFormParams(Exchange exc) throws IOException {
        // @TODO turn around in hasFormParams!
        return !isWWWFormUrlEncoded(exc.getRequest().getHeader().getContentType()) || exc.getRequest().isBodyEmpty();
    }

    public static String createQueryString(String... params) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < params.length; i += 2) {
            if (i != 0) buf.append('&');
            buf.append(URLEncoder.encode(params[i], UTF_8));
            buf.append('=');
            buf.append(URLEncoder.encode(params[i + 1], UTF_8));
        }
        return buf.toString();
    }

    /**
     * Parse a URL query into parameter pairs. The query is expected to be application/x-www-form-urlencoded .
     * <p>
     * Note that this method does not really support multiple parameters with the same key. <b>This method should
     * therefore only be used in contexts where this is not an issue.</b>
     * <p>
     * Background:
     * Note that according to the original RFC 3986 Section 3.4, there is no defined format of the query string.
     * <p>
     * HTML5 defines in <a href="https://html.spec.whatwg.org/#form-submission">form-submission</a> how HTML forms should be serialized.
     * The URLSearchParams class behaviour is defined in <a href="https://url.spec.whatwg.org/#concept-urlsearchparams-list">URLSearchParams</a>
     * where handling of parameters with the same key is supported.
     */
    public static Map<String, String> parseQueryString(String query, DuplicateKeyOrInvalidFormStrategy duplicateKeyOrInvalidFormStrategy) {
        var params = new HashMap<String, String>();

        if (query == null || query.isEmpty())
            return params;

        for (String p : query.split("&")) {
            var m = paramsPat.matcher(p);
            if (m.matches()) {
                var key = decode(m.group(1), UTF_8);
                var value = decode(m.group(2), UTF_8);
                var oldValue = params.get(key);

                if (oldValue == null)
                    params.put(key, value);
                else
                    switch (duplicateKeyOrInvalidFormStrategy) {
                        case ERROR -> throw new RuntimeException("Could not parse query: " + query);
                        case MERGE_USING_COMMA -> params.put(key, oldValue + "," + value);
                    }
            } else {
                if (duplicateKeyOrInvalidFormStrategy == ERROR)
                    throw new RuntimeException("Could not parse query: " + query);
            }
        }
        return params;
    }

    public static Map<String, List<String>> parseQueryString(String query) {
        var params = new HashMap<String, List<String>>();

        if (query == null || query.isEmpty())
            return params;

        for (String p : query.split("&")) {
            var m = paramsPat.matcher(p);
            if (m.matches()) {
                var key = decode(m.group(1), UTF_8);
                var value = decode(m.group(2), UTF_8);
                params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
        return params;
    }

    public static String encode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> p : params.entrySet()) {
            if (first)
                first = false;
            else
                sb.append("&");
            sb.append(URLEncoder.encode(p.getKey(), UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(p.getValue(), UTF_8));
        }

        return sb.toString();
    }
}
