package com.predic8.membrane.core.interceptor.acl2.address;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ipv6Address extends IpAddress {

    private static final Pattern IPV6_PATTERN = Pattern.compile("TODO"); // TODO

    private final Inet6Address address;

    private Ipv6Address(Inet6Address address) {
        this.address = address;
    }

    public static Optional<Ipv6Address> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim();
        if (s.isEmpty()) return Optional.empty();

        Matcher m = IPV6_PATTERN.matcher(s);
        if (!m.matches()) return Optional.empty();

        Inet6Address addr;
        try {
            addr = (Inet6Address) Inet6Address.getByName(m.group("address"));
        } catch (UnknownHostException ignored) {
            return Optional.empty();
        }

        return Optional.of(new Ipv6Address(addr));
    }

    @Override
    public ipVersion version() {
        return ipVersion.IPV6;
    }

    @Override
    public InetAddress getAddress() {
        return address;
    }

}
