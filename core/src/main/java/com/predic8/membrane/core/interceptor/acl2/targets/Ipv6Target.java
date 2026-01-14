package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.empty;
import static java.util.Optional.of;

public class Ipv6Target extends Target {

    private static final Logger log = LoggerFactory.getLogger(Ipv6Target.class);

    private static final Pattern IPV6_CIDR_PATTERN =
            Pattern.compile("^(?<address>\\[?[^/\\s]+\\]?)(?:/(?<cidr>12[0-8]|1[01][0-9]|[1-9]?[0-9]))?$");

    private final Inet6Address target;
    private final int cidr;

    public Ipv6Target(String raw) {
        super(raw);

        String s = raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Invalid IPv6 target: " + raw);

        Matcher m = IPV6_CIDR_PATTERN.matcher(s);
        if (!m.matches()) throw new IllegalArgumentException("Invalid IPv6 target: " + raw);

        String addrStr = IpAddress.removeBracketsIfPresent(m.group("address"));

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

    public static Optional<Target> tryCreate(String target) {
        try {
            return of(new Ipv6Target(target));
        } catch (IllegalArgumentException e) {

            log.debug("Error parsing {} as IPv6 target: {}", target, e.getMessage(), e);
            return empty();
        }
    }

    @Override
    public boolean peerMatches(IpAddress address) {
        if (address == null) return false;
        if (address.version() != IpAddress.ipVersion.IPV6) return false;

        int prefix = this.cidr;
        if (prefix <= 0) return true;
        if (prefix >= 128) return target.equals(address.getInetAddress());

        byte[] a = target.getAddress();
        byte[] b = address.getInetAddress().getAddress();

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
