package com.predic8.membrane.core.interceptor.acl2.address;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ipv4Address implements IpAddress {

    // IPv4 + optional /cidr
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?<address>(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\." +
                    "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9]))$"
    );

    private final Inet4Address address;

    private String hostname = "";

    private Ipv4Address(Inet4Address address) {
        this.address = address;
    }

    public static Optional<Ipv4Address> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim();
        if (s.isEmpty()) return Optional.empty();

        Matcher m = IPV4_PATTERN.matcher(s);
        if (!m.matches()) return Optional.empty();

        Inet4Address addr;
        try {
            addr = (Inet4Address) Inet4Address.getByName(m.group("address"));
        } catch (UnknownHostException ignored) {
            return Optional.empty();
        }

        return Optional.of(new Ipv4Address(addr));
    }

    @Override
    public IpVersion version() {
        return IpVersion.IPV4;
    }

    @Override
    public InetAddress getAddress() {
        return address;
    }

    @Override
    public String getHostname() {
        return "";
    }

    @Override
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
