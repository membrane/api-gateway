package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.exchange.Exchange;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpV4 extends Target {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(?<address>\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(\\/?(?<cidr>\\d{1,2}))?$");

    private final Inet4Address address;
    private final int cidr;

    public static boolean accepts(String address) {
        return IPV4_PATTERN.matcher(address).matches();
    }

    public IpV4(String address) {
        super(address);

        Matcher matcher = IPV4_PATTERN.matcher(address);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid IPv4 format: " + address);
        }

        try {
            this.address = (Inet4Address) Inet4Address.getByName(matcher.group("address"));
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
    public boolean peerMatches(Exchange exc) {
        return false;
    }
}