/* Copyright 2014 predic8 GmbH, www.predic8.com

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
import java.util.regex.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * Same behavior as {@link java.net.URI}, but accomodates '{' in paths.
 */
public class URI {
    private java.net.URI uri;

    private String input;
    private String path;
    private String query;
    private String fragment;

    private String scheme;

    private String host;

    private int port = -1;

    private String pathDecoded, queryDecoded, fragmentDecoded;

    private static final Pattern PATTERN = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
    //                                                             12            3  4          5       6   7        8 9
    // if defined, the groups are:
    // 2: scheme, 4: host, 5: path, 7: query, 9: fragment


    URI(boolean allowCustomParsing, String s) throws URISyntaxException {
        try {
            uri = new java.net.URI(s);
        } catch (URISyntaxException e) {
            if (allowCustomParsing && customInit(s))
                return;
            throw e;
        }
    }

    URI(String s, boolean useCustomParsing) throws URISyntaxException {
        if (useCustomParsing) {
            if (!customInit(s))
                throw new URISyntaxException(s, "URI did not match regular expression.");
        } else {
            uri = new java.net.URI(s);
        }
    }

    private boolean customInit(String s) {
        Matcher m = PATTERN.matcher(s);
        if (!m.matches())
            return false;
        input = s;

        scheme = m.group(2);

        processHostAndPort(m.group(4));

        path = m.group(5);
        query = m.group(7);
        fragment = m.group(9);
        return true;
    }

    private void processHostAndPort(String hostAndPort) {
        if (hostAndPort != null) {

            var posAt = hostAndPort.indexOf("@");
            if (posAt > -1) {
                hostAndPort = hostAndPort.substring(posAt + 1);
            }

            var pos = hostAndPort.indexOf(":");
            if (pos > -1) {
                host = hostAndPort.substring(0, pos);
                port = Integer.parseInt(hostAndPort.substring(pos + 1));
            } else {
                host = hostAndPort;
            }
        }
    }

    public String getScheme() {
        if (uri != null)
            return uri.getScheme();
        return scheme;
    }

    public String getHost() {
        if (uri != null)
            return uri.getHost();
        return host;
    }

    public int getPort() {
        if (uri != null) {
            return uri.getPort();
        }
        return port;
    }

    public String getPath() {
        if (uri != null)
            return uri.getPath();
        if (pathDecoded == null)
            pathDecoded = decode(path);
        return pathDecoded;
    }

    public String getRawPath() {
        if (uri != null)
            return uri.getRawPath();
        return path;
    }

    public String getQuery() {
        if (uri != null)
            return uri.getQuery();
        if (queryDecoded == null)
            queryDecoded = decode(query);
        return queryDecoded;
    }

    public String getRawFragment() {
        if (uri != null)
            return uri.getRawFragment();
        return fragment;
    }

    /**
     * Returns the fragment (the part after '#'), decoded like {@link #getPath()} and {@link #getQuery()}.
     */
    public String getFragment() {
        if (uri != null)
            return uri.getRawFragment();
        if (fragmentDecoded == null)
            fragmentDecoded = decode(fragment);
        return fragmentDecoded;
    }


    /*
     * Returns the authority component of this URI.
     *
     * <p>In default mode delegates to {@link java.net.URI#getAuthority()} and may include
     * user-info (e.g. "user:pass@host:port").<br>
     * In custom parsing mode returns only "host[:port]" (userinfo is intentionally omitted).<br>
     * Returns {@code null} if no authority is present (e.g. "mailto:").
     */
    public String getAuthority() {
        if (uri != null)
            return uri.getAuthority();
        if (host == null)
            return null;
        StringBuilder sb = new StringBuilder(host);
        if (port != -1)
            sb.append(':').append(port);
        return sb.toString();
    }

    private String decode(String string) {
        if (string == null)
            return string;
        return URLDecoder.decode(string, UTF_8);
    }

    public String getRawQuery() {
        if (uri != null)
            return uri.getRawQuery();
        return query;
    }

    public String getPathQueryAndFragment() {
        StringBuilder r = new StringBuilder(100);

        if (getPath() != null && !getPath().isBlank()) {
            r.append(getRawPath());
        } else {
            r.append("/");
        }

        // Add query if present
        if (getQuery() != null && !getQuery().isBlank()) {
            r.append("?").append(getRawQuery());
        }

        // Add fragment if present
        if (getFragment() != null && !getFragment().isBlank()) {
            r.append("#").append(getFragment());
        }

        return r.toString();
    }

    @Override
    public String toString() {
        if (uri != null)
            return uri.toString();
        return input;
    }
}
