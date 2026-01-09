package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.exchange.Exchange;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpV4 extends Target {

    static final Pattern IPV4_PATTERN = Pattern.compile("^(?<address>\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(\\/?(?<cidr>\\d{1,2}))?$");

    private final Inet4Address address;
    private final int cidr;

    public IpV4(String address) throws IncompatibleAddressException {
        super(address);

        try {
            this.address = (Inet4Address) Inet4Address.getByName(matcher.group("address"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        String cidr = matcher.group("cidr");
        if (cidr != null) {
            this.cidr = Integer.parseInt(cidr);
        } else {
            this.cidr = 0;
        }
    }

    @Override
    public boolean peerMatches(Exchange exc) {
        try {
            InetAddress cidrAddress = InetAddress.getByName(exc.getRemoteAddrIp());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }



        return false;
    }

    @Override
    public Pattern getConstructionPattern() {
        return IPV4_PATTERN;
    }
}
