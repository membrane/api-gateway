package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.address.IpAddress;
import com.predic8.membrane.core.interceptor.acl2.address.Ipv4Address;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.acl2.address.IpAddress.ipVersion.IPV6;
import static java.lang.Integer.parseInt;


public class Ipv4Target extends Target {

    private static final Pattern IPV4_CIDR_PATTERN = Pattern.compile(
            "^(?<address>(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9]))" +
                    "(?:/(?<cidr>3[0-2]|[12]?[0-9]))?$"
    );

    private final Inet4Address target;

    private final int cidr;

    public Ipv4Target(String raw) {

        super(raw);
        Matcher m = IPV4_CIDR_PATTERN.matcher(raw == null ? "" : raw.trim());
        if (!m.matches())
            throw new IllegalArgumentException("Invalid IPv4 target: " + raw);

        try {
            this.target = (Inet4Address) Inet4Address.getByName(m.group("address"));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv4 target: " + raw, e);
        }

        this.cidr = m.group("cidr") != null ? parseInt(m.group("cidr")) : 32;
    }

    public Inet4Address getTarget() {
        return target;
    }

    public static boolean accepts(String raw) {
        return Ipv4Address.parse(raw).isPresent();
    }

    @Override
    public boolean peerMatches(IpAddress address) {
        if (address.version().equals(IPV6)) return false;
        int prefix = this.cidr;
        if (prefix <= 0) return true;
        if (prefix >= 32) return target.equals(address.getAddress());

        byte[] a = target.getAddress();
        byte[] b = address.getAddress().getAddress(); // TODO

        int mask = (int) (0xFFFFFFFFL << (32 - prefix));

        int ai = ((a[0] & 0xFF) << 24) | ((a[1] & 0xFF) << 16) | ((a[2] & 0xFF) << 8) | (a[3] & 0xFF);
        int bi = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);

        return (ai & mask) == (bi & mask);
    }
}