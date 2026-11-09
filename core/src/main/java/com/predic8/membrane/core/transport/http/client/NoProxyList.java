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

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.interceptor.acl.IpAddress;
import com.predic8.membrane.core.interceptor.acl.targets.Ipv4Target;
import com.predic8.membrane.core.interceptor.acl.targets.Ipv6Target;
import com.predic8.membrane.core.interceptor.acl.targets.Target;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.util.NetworkUtil.removeBracketsIfPresent;
import static java.util.Locale.ROOT;

/**
 * Hosts that are reached directly instead of through the proxy, with the semantics of the
 * <code>NO_PROXY</code> environment variable as curl implements it: a domain entry matches the domain and
 * its subdomains, an IP entry (optionally with a CIDR prefix) matches IP literals only, without DNS lookups.
 * An entry followed by a port, e.g. <code>example.com:8080</code> or <code>[::1]:8080</code>, matches only that port.
 */
public final class NoProxyList {

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern PORT = Pattern.compile("\\d{1,5}");
    private static final int ANY_PORT = -1;

    private final boolean matchAll;
    private final List<Domain> domains;
    private final List<IpRange> ipRanges;

    private record Domain(String name, int port) {
        boolean matches(String host, int targetPort) {
            return portMatches(port, targetPort) && (host.equals(name) || host.endsWith("." + name));
        }
    }

    private record IpRange(Target range, int port) {
        boolean matches(IpAddress address, int targetPort) {
            return portMatches(port, targetPort) && range.peerMatches(address);
        }
    }

    private record HostAndPort(String host, int port) {}

    private NoProxyList(boolean matchAll, List<Domain> domains, List<IpRange> ipRanges) {
        this.matchAll = matchAll;
        this.domains = List.copyOf(domains);
        this.ipRanges = List.copyOf(ipRanges);
    }

    /**
     * @param list comma separated entries, e.g. <code>localhost, .example.com, 10.0.0.0/8</code>
     * @throws ConfigurationException if an entry is neither a domain nor an IP address or range
     */
    public static NoProxyList parse(String list) {
        return parse(list, "noProxy");
    }

    /**
     * @param setting where the list comes from, named in the error message
     */
    public static NoProxyList parse(String list, String setting) {
        var matchAll = false;
        final var domains = new ArrayList<Domain>();
        final var ipRanges = new ArrayList<IpRange>();

        for (var entry : list.split(",")) {
            entry = entry.trim().toLowerCase(ROOT);
            if (entry.isEmpty())
                continue;
            if (entry.equals("*")) {
                matchAll = true;
                continue;
            }
            final var hostAndPort = splitPort(entry, setting);
            final var host = hostAndPort.host();
            // The trailing dot of a host name is dropped, but never the one after a CIDR prefix such as 10.0.0.0/8.
            final var ipRange = parseIpRange(host.contains("/") ? host : normalize(host), setting);
            if (ipRange.isPresent()) {
                ipRanges.add(new IpRange(ipRange.get(), hostAndPort.port()));
                continue;
            }
            domains.add(new Domain(toDomain(host, setting), hostAndPort.port()));
        }
        return new NoProxyList(matchAll, domains, ipRanges);
    }

    public boolean matches(String host, int port) {
        if (matchAll)
            return true;
        final var normalized = normalize(host);
        if (isIpLiteral(normalized))
            return matchesIpRange(normalized, port);
        return domains.stream().anyMatch(domain -> domain.matches(normalized, port));
    }

    // An IPv6 address only takes a port in brackets, e.g. [::1]:8080, as its colons are ambiguous otherwise.
    private static HostAndPort splitPort(String entry, String setting) {
        final int colon;
        if (entry.startsWith("[")) {
            final var end = entry.indexOf(']');
            if (end == -1 || end == entry.length() - 1 || entry.charAt(end + 1) != ':')
                return new HostAndPort(entry, ANY_PORT);
            colon = end + 1;
        } else {
            colon = entry.indexOf(':');
            if (colon == -1 || colon != entry.lastIndexOf(':'))
                return new HostAndPort(entry, ANY_PORT);
        }
        return new HostAndPort(entry.substring(0, colon), parsePort(entry.substring(colon + 1), entry, setting));
    }

    private static int parsePort(String port, String entry, String setting) {
        if (!PORT.matcher(port).matches())
            throw invalidEntry(entry, setting);
        final var number = Integer.parseInt(port);
        if (number < 1 || number > 65535)
            throw invalidEntry(entry, setting);
        return number;
    }

    private static boolean portMatches(int entryPort, int targetPort) {
        return entryPort == ANY_PORT || entryPort == targetPort;
    }

    private static Optional<Target> parseIpRange(String entry, String setting) {
        if (IPV4_LITERAL.matcher(entry.split("/")[0]).matches())
            return Optional.of(Ipv4Target.tryCreate(entry).orElseThrow(() -> invalidEntry(entry, setting)));
        // Ipv6Target would resolve anything that is not an IPv6 literal via DNS, so only hand it entries with a colon.
        if (entry.contains(":"))
            return Optional.of(Ipv6Target.tryCreate(entry).orElseThrow(() -> invalidEntry(entry, setting)));
        return Optional.empty();
    }

    private static String toDomain(String entry, String setting) {
        // "*.example.com" is the http.nonProxyHosts spelling, ".example.com" the NO_PROXY one. Both mean the same here.
        var domain = entry.startsWith("*.") ? entry.substring(2) : entry;
        domain = normalize(domain.startsWith(".") ? domain.substring(1) : domain);
        if (domain.isEmpty() || domain.contains("*") || domain.contains("/"))
            throw invalidEntry(entry, setting);
        return domain;
    }

    private boolean matchesIpRange(String host, int port) {
        final IpAddress address;
        try {
            address = IpAddress.parse(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return ipRanges.stream().anyMatch(range -> range.matches(address, port));
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || IPV4_LITERAL.matcher(host).matches();
    }

    private static String normalize(String host) {
        host = removeBracketsIfPresent(host.trim().toLowerCase(ROOT));
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static ConfigurationException invalidEntry(String entry, String setting) {
        return new ConfigurationException(("The entry '%s' in %s is neither a host name, a domain like .example.com, "
                + "nor an IP address or range like 10.0.0.0/8, each optionally followed by a port like :8080.")
                .formatted(entry, setting));
    }
}
