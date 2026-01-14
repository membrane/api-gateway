package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.core.util.DNSCache;

import java.util.Optional;

public final class PeerAddressResolver {

    private final boolean checkHostname;
    private final DNSCache dnsCache;

    public PeerAddressResolver(boolean checkHostname, DNSCache dnsCache) {
        this.checkHostname = checkHostname;
        this.dnsCache = dnsCache;
    }

    public Optional<IpAddress> resolve(String rawRemoteIp) {
        if (rawRemoteIp == null) return Optional.empty();
        String s = rawRemoteIp.trim();
        if (s.isEmpty()) return Optional.empty();

        IpAddress ip;
        try {
            ip = IpAddress.parse(s);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (checkHostname) {
            ip.setHostname(dnsCache.getCanonicalHostName(ip.getAddress()));
        }

        return Optional.of(ip);
    }
}
