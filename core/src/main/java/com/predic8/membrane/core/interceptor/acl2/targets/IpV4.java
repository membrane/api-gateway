package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.exchange.Exchange;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;

public class IpV4 extends Target {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(?<address>(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9]))(?:/(?<cidr>3[0-2]|[12]?[0-9]))?$");

    private final Inet4Address address;
    private final int cidr;

    public static boolean accepts(String address) {
        Matcher matcher = IPV4_PATTERN.matcher(address);
        return matcher.matches();
    }

    public IpV4(String address) {
        super(address);

        Matcher matcher = IPV4_PATTERN.matcher(address);

        try {
            this.address = (Inet4Address) Inet4Address.getByName(matcher.group("address"));
        } catch (UnknownHostException e) {
            throw new RuntimeException("Invalid IP address value: " + address, e);
        }

        String cidrGroup = matcher.group("cidr");
        if (cidrGroup != null) {
            this.cidr = parseInt(cidrGroup);
        } else {
            this.cidr = 32;
        }
    }

    @Override
    public boolean peerMatches(Exchange exc) {
        try {
            IPV4_PATTERN.matcher()
            Inet4Address.getByName(exc.getRemoteAddrIp());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        return false;
    }
}