package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.address.IpAddress;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ipv6Target extends Target {

    private static final Pattern IPV6_PATTERN = Pattern.compile("^.*$"); // TODO

    private final Inet6Address address;
    private final int cidr;

    public static boolean accepts(String address) {
        return IPV6_PATTERN.matcher(address).matches();
    }

    public Ipv6Target(String address) {
        super(address);

        Matcher matcher = IPV6_PATTERN.matcher(address);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid IPv6 format: " + address);
        }

        try {
            this.address = (Inet6Address) Inet4Address.getByName(matcher.group("address"));
        } catch (UnknownHostException e) {
            throw new RuntimeException("Invalid IP address value: " + address, e);
        }

        String cidrGroup = matcher.group("cidr");
        if (cidrGroup != null) {
            this.cidr = Integer.parseInt(cidrGroup);
        } else {
            this.cidr = 32;
        }
    }

    @Override
    public boolean peerMatches(IpAddress address) {
        return false;
    }
}