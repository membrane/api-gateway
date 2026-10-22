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
 */
public final class NoProxyList {

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final boolean matchAll;
    private final List<String> domains;
    private final List<Target> ipRanges;

    private NoProxyList(boolean matchAll, List<String> domains, List<Target> ipRanges) {
        this.matchAll = matchAll;
        this.domains = List.copyOf(domains);
        this.ipRanges = List.copyOf(ipRanges);
    }

    /**
     * @param list comma separated entries, e.g. <code>localhost, .example.com, 10.0.0.0/8</code>
     * @throws ConfigurationException if an entry is neither a domain nor an IP address or range
     */
    public static NoProxyList parse(String list) {
        var matchAll = false;
        final var domains = new ArrayList<String>();
        final var ipRanges = new ArrayList<Target>();

        for (var entry : list.split(",")) {
            entry = entry.trim().toLowerCase(ROOT);
            if (entry.isEmpty())
                continue;
            if (entry.equals("*")) {
                matchAll = true;
                continue;
            }
            final var ipRange = parseIpRange(entry);
            if (ipRange.isPresent()) {
                ipRanges.add(ipRange.get());
                continue;
            }
            domains.add(toDomain(entry));
        }
        return new NoProxyList(matchAll, domains, ipRanges);
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
        if (IPV4_LITERAL.matcher(entry.split("/")[0]).matches())
            return Optional.of(Ipv4Target.tryCreate(entry).orElseThrow(() -> invalidEntry(entry)));
        // Ipv6Target would resolve anything that is not an IPv6 literal via DNS, so only hand it entries with a colon.
        if (entry.contains(":"))
            return Optional.of(Ipv6Target.tryCreate(entry).orElseThrow(() -> invalidEntry(entry)));
        return Optional.empty();
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
        return ipRanges.stream().anyMatch(range -> range.peerMatches(address));
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || IPV4_LITERAL.matcher(host).matches();
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
