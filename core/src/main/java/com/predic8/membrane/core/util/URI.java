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

    record HostPort(String host, Integer port) {
    }

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
        if (s == null) return false;
        s = s.trim();

        Matcher m = PATTERN.matcher(s);
        if (!m.matches()) return false;

        input = s;

        scheme = m.group(2);

        HostPort hp = parseHostPort(m.group(4));

        if (hp != null) {
            this.host = hp.host();
            Integer p = hp.port();
            this.port = (p != null) ? p : -1;
        } else {
            this.host = null;
            this.port = -1;
        }

        path = m.group(5);
        query = m.group(7);
        fragment = m.group(9);
        return true;
    }

    HostPort parseHostPort(String authority) {
        if (authority == null || authority.isEmpty()) return null;

        String noUser = stripUserInfo(authority);

        return isBracketedIpv6(noUser) ? parseIpv6(noUser) : parseIpv4(noUser);
    }

    HostPort parseIpv6(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input must not be null.");
        }

        // Must start with '[' and contain exactly one '['
        if (input.isEmpty() || input.charAt(0) != '[' || input.indexOf('[', 1) != -1) {
            throw new IllegalArgumentException("Invalid IPv6 bracket literal.");
        }

        int rb = input.lastIndexOf(']');
        if (rb < 0) {
            throw new IllegalArgumentException("Invalid IPv6 bracket literal: missing closing bracket.");
        }

        // No extra ']' after the one we chose
        if (input.indexOf(']', rb + 1) != -1) {
            throw new IllegalArgumentException("Invalid IPv6 bracket literal: multiple closing brackets.");
        }

        String host = input.substring(1, rb);
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Host must not be empty.");
        }

        // Optionally normalize RFC 6874 zone-id: allow %25 and decode to %
        // If you prefer strictness, remove this normalization block.
        if (host.contains("%25")) {
            host = host.replace("%25", "%");
        }

        if (rb + 1 == input.length()) {
            // No port
            return new HostPort(host, null);
        }

        // If anything follows, it must be ":" + digits, with nothing after
        if (input.charAt(rb + 1) != ':') {
            throw new IllegalArgumentException("Invalid authority: only ':<port>' may follow the IPv6 literal.");
        }

        if (rb + 2 >= input.length()) {
            throw new IllegalArgumentException("Port must not be empty after ':'.");
        }

        String portPart = input.substring(rb + 2);

        // Enforce port is only digits
        for (int i = 0; i < portPart.length(); i++) {
            char c = portPart.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Port must contain only digits.");
            }
        }

        return new HostPort(host, parsePort(portPart));
    }

    HostPort parseIpv4(String authority) {
        int lastColon = authority.lastIndexOf(':');

        if (lastColon == -1) {
            // No port
            if (authority.isEmpty()) {
                throw new IllegalArgumentException("Host must not be empty.");
            }
            return new HostPort(authority, null);
        }

        // Ensure only one colon (IPv4 or hostname, not IPv6)
        if (authority.indexOf(':') != lastColon) {
            throw new IllegalArgumentException("Invalid authority: multiple colons found.");
        }

        String host = authority.substring(0, lastColon);
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Host must not be empty.");
        }

        String portPart = authority.substring(lastColon + 1);
        if (portPart.isEmpty()) {
            throw new IllegalArgumentException("Port must not be empty if ':' is present.");
        }

        Integer port = parsePort(portPart);
        return new HostPort(host, port);
    }

    Integer parsePort(String rawPort) {
        try {
            int port = Integer.parseInt(rawPort);
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid port: " + rawPort);
            }
            return port;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid port: " + rawPort, nfe);
        }
    }

    String stripUserInfo(String input) {
        int atSymbolPos = input.indexOf("@");
        return (atSymbolPos > -1) ? input.substring(atSymbolPos + 1) : input;
    }

    boolean isBracketedIpv6(String input) {
        return input.startsWith("[");
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
            return uri.getFragment();
        if (fragmentDecoded == null)
            fragmentDecoded = decode(fragment);
        return fragmentDecoded;
    }

    /*
     * Returns the authority component of this URI.
     *
     * <p>In default mode delegates to {@link java.net.URI#getAuthority()} and may include
     * user-info (e.g. "user:pass@host:port").

     * In custom parsing mode returns only "host[:port]" (userinfo is intentionally omitted).

     * Returns {@code null} if no authority is present (e.g. "mailto:").
     */
    public String getAuthority() {
        if (uri != null) return uri.getAuthority();
        if (host == null) return null;

        String h = isIPv6Address(host) ? "[" + host + "]" : host;

        return port == -1 ? h : h + ":" + port;
    }

    private boolean isIPv6Address(String host) {
        return host.indexOf(':') >= 0;
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
     *
     * @return
     */
    public String getPathWithQuery() {
        StringBuilder r = new StringBuilder(100);

        if (getRawPath() != null && !getRawPath().isBlank()) {
            r.append(getRawPath());
        } else {
            r.append("/");
        }

        // Add query if present
        if (getRawQuery() != null && !getRawQuery().isBlank()) {
            r.append("?").append(getRawQuery());
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