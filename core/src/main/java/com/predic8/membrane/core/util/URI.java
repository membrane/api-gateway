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

import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Same behavior as {@link java.net.URI}, but accommodates '{' in paths.
 */
public class URI {
    private java.net.URI uri;

    private String input;
    private String path;
    private String query;
    private String fragment;

    private String userInfo;

    private String scheme;

    private HostPort hostPort;

    private String pathDecoded, queryDecoded, fragmentDecoded;

    // raw authority string as it appeared in the input (may include user-info)
    private String authority;

    private static final Pattern PATTERN = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
    //                                                             12            3  4          5       6   7        8 9
    // if defined, the groups are:
    // 2: scheme, 4: authority, 5: path, 7: query, 9: fragment

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

        authority = m.group(4);
        processAuthority(authority);

        path = m.group(5);
        query = m.group(7);
        fragment = m.group(9);
        return true;
    }

    /**
     * Parses authority into userInfo, host and port.
     * Keeps original authority untouched for getAuthority().
     */
    private void processAuthority(String rawAuthority) {
        if (rawAuthority == null)
            return;

        int at = rawAuthority.indexOf('@');
        if (at >= 0) {
            userInfo = rawAuthority.substring(0, at);
        }

        hostPort = parseHostPort(rawAuthority);
    }

    record HostPort(String host, int port) {}

    static HostPort parseHostPort(String rawAuthority) {
        if (rawAuthority == null)
            throw new IllegalArgumentException("rawAuthority is null.");
        String hostAndPort = stripUserInfo(rawAuthority);

        if (isIPLiteral(hostAndPort)) {
            return parseIpv6(hostAndPort);
        }

        return parseIPv4OrHostname(hostAndPort);
    }

    static String stripUserInfo(String authority) {
        int at = authority.indexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    static HostPort parseIPv4OrHostname(String hostAndPort) {
        String host;
        int port;
        int colon = hostAndPort.indexOf(':');
        if (colon >= 0) {
            host = hostAndPort.substring(0, colon);
            String p = hostAndPort.substring(colon + 1);
            port = validatePortDigits(p);
        } else {
            host = hostAndPort;
            port = -1;
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Host must not be empty.");
        }
        return new HostPort(host, port);
    }

    static HostPort parseIpv6(String hostAndPort) {
        int end = hostAndPort.indexOf(']');
        if (end < 0) {
            throw new IllegalArgumentException("Invalid IPv6 bracket literal: missing ']'.");
        }
        String ipv6 = hostAndPort.substring(0, end+1);

        if (ipv6.length() <= 2) {
            throw new IllegalArgumentException("Host must not be empty.");
        }

        int port = parsePort(hostAndPort.substring(end + 1));
        return new HostPort(ipv6, port);
    }

    static boolean isIPLiteral(String hostAndPort) {
        return hostAndPort.startsWith("[");
    }

    static int parsePort(String restOfAuthority) {
        if (restOfAuthority.isEmpty())
            return -1;
        if (restOfAuthority.charAt(0) == ':') {
            return validatePortDigits(restOfAuthority.substring(1));
        } else {
            throw new IllegalArgumentException("Invalid authority: only ':<port>' may follow the IPv6 literal.");
        }
    }

    private static int validatePortDigits(String p) {
        if (!p.isEmpty()) {
            if (!p.matches("\\d{1,5}"))
                throw new IllegalArgumentException("Invalid port: " + p);
            int candidate = Integer.parseInt(p);
            if (candidate < 0 || candidate > 65535)
                throw new IllegalArgumentException("Port out of range: " + candidate);
            return candidate;
        }
        throw new IllegalArgumentException("Invalid port: ''.");
    }

    public String getScheme() {
        if (uri != null)
            return uri.getScheme();
        return scheme;
    }

    /**
     * As {@link java.net.URI#getHost()} this method
     * - might return an IPv6 literal including the square brackets '[' and ']',
     * - might return something like "[fe80::1%25eth0]".
     */
    public String getHost() {
        if (uri != null)
            return uri.getHost();
        return hostPort.host;
    }

    public int getPort() {
        if (uri != null) {
            return uri.getPort();
        }
        return hostPort.port;
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
            return uri.getFragment();
        if (fragmentDecoded == null)
            fragmentDecoded = decode(fragment);
        return fragmentDecoded;
    }

    /*
     * Returns the authority component of this URI.
     *
     * Default mode delegates to {@link java.net.URI#getAuthority()}.
     * Custom parsing mode returns the original raw authority (may include user-info).
     * Returns {@code null} if no authority is present (e.g. "mailto:").
     */
    public String getAuthority() {
        if (uri != null) return uri.getAuthority();
        return authority;
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

    /**
     * Fragments are client side only and should not be propagated to the backend.
     */
    public String getPathWithQuery() {
        StringBuilder r = new StringBuilder(100);

        if (getRawPath() != null && !getRawPath().isBlank()) {
            r.append(getRawPath());
        } else {
            r.append("/");
        }

        if (getRawQuery() != null && !getRawQuery().isBlank()) {
            r.append('?').append(getRawQuery());
        }
        return r.toString();
    }

    @Override
    public String toString() {
        if (uri != null)
            return uri.toString();
        return input;
    }

    /**
     * RFC 3986, Section 5.2.4 - Remove dot segments from a path.
     * <pre>
     * /a/b/c/./../../g    -> /a/g
     * mid/content=5/../6  -> mid/6
     * /../a               -> /a      (cannot go above the root)
     * /a/..               -> /
     * /a/.                -> /a/
     * /a//../b            -> /a/b    (the empty segment counts as a segment)
     * /a/.../b            -> /a/.../b
     * /a/%2e%2e/b         -> /a/%2e%2e/b  (encoded dots are not decoded, see URIUtil.normalizeRequestTarget)
     * </pre>
     * Expects a path only: on a whole URI like http://h/../b a ".." would remove the host.
     */
    public static String removeDotSegments(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        var out = new StringBuilder();
        int i = 0;
        while (i < path.length()) {
            // A: remove prefix "../" or "./"
            if (path.startsWith("../", i)) {
                i += 3;
            } else if (path.startsWith("./", i)) {
                i += 2;
            }
            // B: remove prefix "/./" or "/."(end)
            else if (path.startsWith("/./", i)) {
                i += 2;
            } else if (i + 2 == path.length() && path.startsWith("/.", i)) {
                out.append('/');
                i += 2;
            }
            // C: remove prefix "/../" or "/.."(end), and remove last output segment
            else if (path.startsWith("/../", i)) {
                i += 3;
                removeLastSegment(out);
            } else if (i + 3 == path.length() && path.startsWith("/..", i)) {
                removeLastSegment(out);
                out.append('/');
                i += 3;
            }
            // D: "." or ".." only
            else if ((i == path.length() - 1 && path.charAt(i) == '.') ||
                     (i == path.length() - 2 && path.charAt(i) == '.' && path.charAt(i + 1) == '.')) {
                i = path.length();
            }
            // E: move first path segment (including initial "/" if any) to output
            else {
                if (path.charAt(i) == '/') {
                    out.append('/');
                    i++;
                }
                while (i < path.length() && path.charAt(i) != '/') {
                    out.append(path.charAt(i));
                    i++;
                }
            }
        }
        return out.toString();
    }

    private static void removeLastSegment(StringBuilder out) {
        out.setLength(Math.max(out.lastIndexOf("/"), 0));
    }
}
