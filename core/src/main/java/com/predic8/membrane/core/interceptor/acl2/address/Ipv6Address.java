package com.predic8.membrane.core.interceptor.acl2.address;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.regex.Pattern;

public final class Ipv6Address extends IpAddress {

    private static final Pattern IPV6_PATTERN = Pattern.compile("TODO");

    private final Inet6Address address;

    private Ipv6Address(Inet6Address address) {
        this.address = address;
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
