package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.address.IpAddress;
import com.predic8.membrane.core.interceptor.acl2.address.Ipv6Address;
import org.jetbrains.annotations.NotNull;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.acl2.address.Ipv6Address.removeBracketsIfPresent;

public class Ipv6Target extends Target {

    private static final Pattern IPV6_CIDR_PATTERN = Pattern.compile("^(?<address>\\[?[^/\\s]+\\]?)(?:/(?<cidr>12[0-8]|1[01][0-9]|[1-9]?[0-9]))?$");

    private final Inet6Address target;
    private final int cidr;

    public static boolean accepts(String raw) {
        if (raw == null) return false;

        Matcher m = IPV6_CIDR_PATTERN.matcher(raw.trim());
        if (!m.matches()) return false;

        String addrStr = m.group("address");
        addrStr = removeBracketsIfPresent(addrStr);

        try {
            return InetAddress.getByName(addrStr) instanceof Inet6Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public Ipv6Target(String raw) {
        super(raw);

        Matcher m = IPV6_CIDR_PATTERN.matcher(raw == null ? "" : raw.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid IPv6 target: " + raw);
        }

        String addrStr = m.group("address");
        addrStr = removeBracketsIfPresent(addrStr);

        InetAddress inet;
        try {
            inet = InetAddress.getByName(addrStr);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv6 target: " + raw, e);
        }

        if (!(inet instanceof Inet6Address inet6)) {
            throw new IllegalArgumentException("Invalid IPv6 target: " + raw);
        }
        this.target = inet6;

        String cidrGroup = m.group("cidr");
        this.cidr = cidrGroup != null ? Integer.parseInt(cidrGroup) : 128;
    }

    @Override
    public boolean peerMatches(IpAddress address) {
        if (address == null) return false;
        if (address.version() != IpAddress.ipVersion.IPV6) return false;

        int prefix = this.cidr;
        if (prefix <= 0) return true;
        if (prefix >= 128) return target.equals(address.getAddress());

        byte[] a = target.getAddress();
        byte[] b = address.getAddress().getAddress();

        int fullBytes = prefix / 8;
        int remainingBits = prefix % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) return false;
        }

        if (remainingBits == 0) return true;

        int mask = 0xFF << (8 - remainingBits);
        return (a[fullBytes] & mask) == (b[fullBytes] & mask);
    }
}
