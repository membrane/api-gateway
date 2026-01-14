package com.predic8.membrane.core.interceptor.acl2.address;

import org.jetbrains.annotations.NotNull;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ipv6Address extends IpAddress {

    private static final Pattern IPV6_PATTERN = Pattern.compile("^(?<address>\\[?[^/\\s]+\\]?)$");

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

        String addrStr = m.group("address");
        addrStr = removeBracketsIfPresent(addrStr);

        InetAddress inet;
        try {
            inet = InetAddress.getByName(addrStr);
        } catch (UnknownHostException ignored) {
            return Optional.empty();
        }

        if (!(inet instanceof Inet6Address inet6)) return Optional.empty();
        return Optional.of(new Ipv6Address(inet6));
    }

    public static @NotNull String removeBracketsIfPresent(String addrStr) {
        if (addrStr.startsWith("[") && addrStr.endsWith("]") && addrStr.length() >= 2) {
            addrStr = addrStr.substring(1, addrStr.length() - 1).trim();
        }
        return addrStr;
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
