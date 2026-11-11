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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.util.NetworkUtil.isIpLiteral;
import static com.predic8.membrane.core.util.NetworkUtil.isIpv4Literal;
import static com.predic8.membrane.core.util.NetworkUtil.removeBracketsIfPresent;
import static java.util.Locale.ROOT;

/**
 * Hosts that are reached directly instead of through the proxy, with the semantics of the
 * <code>NO_PROXY</code> environment variable as curl implements it: a domain entry matches the domain and
 * its subdomains, an IP entry (optionally with a CIDR prefix) matches IP literals only, without DNS lookups.
 */
public final class NoProxyList {

    private final boolean matchAll;
    private final List<String> domains;
    private final List<Target> ipRanges;
    private final List<Target> ipv4MappedRanges;

    private NoProxyList(boolean matchAll, List<String> domains, List<Target> ipRanges, List<Target> ipv4MappedRanges) {
        this.matchAll = matchAll;
        this.domains = List.copyOf(domains);
        this.ipRanges = List.copyOf(ipRanges);
        this.ipv4MappedRanges = List.copyOf(ipv4MappedRanges);
    }

    /**
     * @param list comma separated entries, e.g. <code>localhost, .example.com, 10.0.0.0/8</code>
     * @throws ConfigurationException if an entry is neither a domain nor an IP address or range
     */
    public static NoProxyList parse(String list) {
        var matchAll = false;
        final var domains = new ArrayList<String>();
        final var ipRanges = new ArrayList<Target>();
        final var ipv4MappedRanges = new ArrayList<Target>();

        for (var entry : list.split(",")) {
            entry = entry.trim().toLowerCase(ROOT);
            if (entry.isEmpty())
                continue;
            if (entry.equals("*")) {
                matchAll = true;
                continue;
            }
            final var ipv4MappedRange = parseIpv4MappedRange(normalize(entry));
            if (ipv4MappedRange.isPresent()) {
                ipv4MappedRanges.add(ipv4MappedRange.get());
                continue;
            }
            final var ipRange = parseIpRange(normalize(entry));
            if (ipRange.isPresent()) {
                ipRanges.add(ipRange.get());
                continue;
            }
            domains.add(toDomain(entry));
        }
        return new NoProxyList(matchAll, domains, ipRanges, ipv4MappedRanges);
    }

    public boolean matches(String host) {
        if (matchAll)
            return true;
        host = normalize(host);
        if (isIpLiteral(host))
            return matchesIpRange(host);
        for (var domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain))
                return true;
        }
        return false;
    }

    private static Optional<Target> parseIpRange(String entry) {
        if (isIpv4Literal(entry.split("/")[0]))
            return Optional.of(Ipv4Target.tryCreate(entry).orElseThrow(() -> invalidEntry(entry)));
        // Ipv6Target would resolve anything that is not an IPv6 literal via DNS, so only hand it entries with a colon.
        if (entry.contains(":"))
            return Optional.of(Ipv6Target.tryCreate(entry).orElseThrow(() -> invalidEntry(entry)));
        return Optional.empty();
    }

    /**
     * Java resolves an IPv4-mapped IPv6 literal like <code>::ffff:10.0.0.1</code> to an <code>Inet4Address</code>,
     * which <code>Ipv6Target</code> rejects. Such an entry becomes an IPv4 range on the embedded address instead.
     */
    private static Optional<Target> parseIpv4MappedRange(String entry) {
        if (!entry.contains(":"))
            return Optional.empty();
        final var parts = entry.split("/", 2);
        if (!(parseIpv6Literal(parts[0]) instanceof Inet4Address ipv4))
            return Optional.empty();
        if (parts.length == 2 && !parts[1].matches("\\d{1,3}"))
            throw invalidEntry(entry);
        final var cidr = parts.length == 2 ? Integer.parseInt(parts[1]) : 128;
        // A shorter prefix also covers addresses outside ::ffff:0:0/96, which an IPv4 range cannot express.
        if (cidr < 96 || cidr > 128)
            throw invalidEntry(entry);
        return Optional.of(Ipv4Target.tryCreate("%s/%d".formatted(ipv4.getHostAddress(), cidr - 96))
                .orElseThrow(() -> invalidEntry(entry)));
    }

    private static InetAddress parseIpv6Literal(String address) {
        try {
            // The brackets make Java parse a literal only, never fall back to a DNS lookup.
            return InetAddress.getByName("[" + removeBracketsIfPresent(address) + "]");
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String toDomain(String entry) {
        // "*.example.com" is the http.nonProxyHosts spelling, ".example.com" the NO_PROXY one. Both mean the same here.
        var domain = entry.startsWith("*.") ? entry.substring(2) : entry;
        domain = normalize(domain.startsWith(".") ? domain.substring(1) : domain);
        if (domain.isEmpty() || domain.contains("*") || domain.contains("/"))
            throw invalidEntry(entry);
        return domain;
    }

    private boolean matchesIpRange(String host) {
        final IpAddress address;
        try {
            address = IpAddress.parse(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (ipRanges.stream().anyMatch(range -> range.peerMatches(address)))
            return true;
        // A mapped host parses to an IPv4 address as well, so only its colon tells it apart from a plain IPv4 host.
        return host.contains(":") && ipv4MappedRanges.stream().anyMatch(range -> range.peerMatches(address));
    }

    private static String normalize(String host) {
        host = removeBracketsIfPresent(host.trim().toLowerCase(ROOT));
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static ConfigurationException invalidEntry(String entry) {
        return new ConfigurationException(("The noProxy entry '%s' is neither a host name, a domain like .example.com, "
                + "nor an IP address or range like 10.0.0.0/8.").formatted(entry));
    }
}
