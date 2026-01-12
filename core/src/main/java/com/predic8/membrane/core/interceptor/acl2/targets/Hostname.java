package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.exchange.Exchange;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Hostname extends Target {

    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^.*$");

    //private final Inet4Address address;
    private final int cidr;

    public static boolean accepts(String address) {
        return HOSTNAME_PATTERN.matcher(address).matches();
    }

    public Hostname(String address) {
        super(address);

        Matcher matcher = HOSTNAME_PATTERN.matcher(address);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid IPv4 format: " + address);
        }

//        try {
//            this.address = (InetAddress) InetAddress.getByName(matcher.group("address"));
//        } catch (UnknownHostException e) {
//            throw new RuntimeException("Invalid IP address value: " + address, e);
//        }

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