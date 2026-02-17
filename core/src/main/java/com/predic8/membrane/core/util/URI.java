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

import static com.predic8.membrane.core.util.URIValidationUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * Same behavior as {@link java.net.URI}, but accommodates '{' in paths.
 */
public class URI {

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

    private boolean allowIllegalCharacters = false;

    private static final Pattern PATTERN = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
    //                                                             12            3  4          5       6   7        8 9
    // if defined, the groups are:
    // 2: scheme, 4: authority, 5: path, 7: query, 9: fragment

    URI(String s) throws URISyntaxException {
        if (!customInit(s))
            throw new URISyntaxException(s, "URI did not match regular expression.");
    }

    URI(String s, boolean allowIllegalCharacters) throws URISyntaxException {
        this.allowIllegalCharacters = allowIllegalCharacters;
        if (!customInit(s))
            throw new URISyntaxException(s, "URI did not match regular expression.");
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

        if (!allowIllegalCharacters) {
            var options = new UriIllegalCharacterDetector.Options();
            UriIllegalCharacterDetector.validateAll(this, options);
        }

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

    record HostPort(String host, int port) {
    }

    HostPort parseHostPort(String rawAuthority) {
        if (rawAuthority == null)
            throw new IllegalArgumentException("rawAuthority is null.");
        String hostAndPort = stripUserInfo(rawAuthority);

        if (isIP6Literal(hostAndPort)) {
            return parseIpv6(hostAndPort);
        }

        return parseIPv4OrHostname(hostAndPort);
    }

    static String stripUserInfo(String authority) {
        int at = authority.indexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    HostPort parseIPv4OrHostname(String hostAndPort) {
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
        if (!allowIllegalCharacters)
            validateHost( host);
        return new HostPort(host, port);
    }

    static HostPort parseIpv6(String hostAndPort) {
        int end = hostAndPort.indexOf(']');
        if (end < 0) {
            throw new IllegalArgumentException("Invalid IPv6 bracket literal: missing ']'.");
        }
        String ipv6 = hostAndPort.substring(0, end + 1);

        if (ipv6.length() <= 2) {
            throw new IllegalArgumentException("Host must not be empty.");
        }

        validateIP6Address(ipv6);

        int port = parsePort(hostAndPort.substring(end + 1));
        return new HostPort(ipv6, port);
    }

    static boolean isIP6Literal(String hostAndPort) {
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
        if (p.isEmpty())
            throw new IllegalArgumentException("Invalid port: ''.");

        validateDigits(p);
        int i = Integer.parseInt(p);
        if (i > 65535)
            throw new IllegalArgumentException("Invalid port: '%s'.".formatted(p));
        return i;
    }

    public String getScheme() {
        return scheme;
    }

    /**
     * As {@link java.net.URI#getHost()} this method
     * - might return an IPv6 literal including the square brackets '[' and ']',
     * - might return something like "[fe80::1%25eth0]".
     */
    public String getHost() {
        return hostPort.host;
    }

    public int getPort() {
        return hostPort.port;
    }

    public String getPath() {
        if (pathDecoded == null)
            pathDecoded = decode(path);
        return pathDecoded;
    }

    public String getRawPath() {
        return path;
    }

    public String getQuery() {
        if (queryDecoded == null)
            queryDecoded = decode(query);
        return queryDecoded;
    }

    public String getRawFragment() {
        return fragment;
    }

    /**
     * Returns the fragment (the part after '#'), decoded like {@link #getPath()} and {@link #getQuery()}.
     */
    public String getFragment() {
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
        return authority;
    }

    private String decode(String string) {
        if (string == null)
            return null;
        return URLDecoder.decode(string, UTF_8);
    }

    public String getRawQuery() {
        return query;
    }

    /**
     * Fragments are client side only and should not be propagated to the backend.
     */
    public String getPathWithQuery() {
        var r = new StringBuilder(100);

        if (getRawPath() != null && !getRawPath().isBlank()) {
            r.append(getRawPath());
        } else {
            r.append("/");
        }

        if (getRawQuery() == null)
            return r.toString();

        return r.append('?').append(getRawQuery()).toString();
    }

    /**
     * Returns the first part of the URI till the first slash or # or
     *
     * @return
     */
    public String getWithoutPath() {
        return getScheme() + "://" + getAuthority();
    }

    /**
     * Use ResolverMap to combine URIs. Only resort to this function if it is not possible to use ResolverMap e.g.
     * for URIs with invalid characters like $ { } in the DispatchingInterceptor
     *
     * @param relative URI
     * @return Combined URI
     * @throws URISyntaxException
     */
    public URI resolve(URI relative) throws URISyntaxException {
        return resolve(relative, new URIFactory(true));
    }

    public URI resolve(URI relative, URIFactory factory) throws URISyntaxException {
        // RFC 3986, Section 5.2.2 - resolve a relative reference against a base URI.
        // Uses getter methods to read components regardless of parsing mode.

        String rScheme = relative.getScheme();
        String rAuthority = relative.getAuthority();
        String rPath = relative.getRawPath();
        String rQuery = relative.getRawQuery();
        String rFragment = relative.getRawFragment();

        String tScheme, tAuthority, tPath, tQuery, tFragment;

        if (rScheme != null) {
            tScheme = rScheme;
            tAuthority = rAuthority;
            tPath = removeDotSegments(rPath);
            tQuery = rQuery;
        } else {
            if (rAuthority != null) {
                tScheme = this.getScheme();
                tAuthority = rAuthority;
                tPath = removeDotSegments(rPath);
                tQuery = rQuery;
            } else {
                if (rPath == null || rPath.isEmpty()) {
                    tPath = this.getRawPath();
                    tQuery = rQuery != null ? rQuery : this.getRawQuery();
                } else {
                    if (rPath.startsWith("/")) {
                        tPath = removeDotSegments(rPath);
                    } else {
                        var merged = merge(this.getAuthority(), this.getRawPath(), rPath);
                        if (this.getScheme().equals("classpath")) {
                            tPath = merged;
                        } else {
                            tPath = removeDotSegments(merged);
                        }
                    }
                    tQuery = rQuery;
                }
                tScheme = this.getScheme();
                tAuthority = this.getAuthority();
            }
        }
        tFragment = rFragment;

        // Recompose per RFC 3986, Section 5.3
        var result = new StringBuilder();
        if (tScheme != null) {
            result.append(tScheme).append(':');
        }
        if (tAuthority != null) {
            result.append("//").append(tAuthority);
        }
        if (tPath != null) {
            result.append(tPath);
        }
        if (tQuery != null) {
            result.append('?').append(tQuery);
        }
        if (tFragment != null) {
            result.append('#').append(tFragment);
        }

        return factory.create(result.toString());
    }

    /**
     * RFC 3986, Section 5.2.3 - Merge base path with relative reference.
     */
    private static String merge(String baseAuthority, String basePath, String relativePath) {
        if (baseAuthority != null && (basePath == null || basePath.isEmpty())) {
            return "/" + relativePath;
        }
        if (basePath == null) {
            return relativePath;
        }
        int lastSlash = basePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            return basePath.substring(0, lastSlash + 1) + relativePath;
        }
        return relativePath;
    }

    /**
     * RFC 3986, Section 5.2.4 - Remove dot segments from a path.
     */
    public static String removeDotSegments(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        StringBuilder out = new StringBuilder();
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
        int lastSlash = out.lastIndexOf("/");
        if (lastSlash >= 0) {
            out.setLength(lastSlash);
        } else {
            out.setLength(0);
        }
    }

    @Override
    public String toString() {
        return input;
    }
}
