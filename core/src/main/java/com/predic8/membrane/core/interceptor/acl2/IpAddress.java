package com.predic8.membrane.core.interceptor.acl2;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import static com.predic8.membrane.core.interceptor.acl2.IpAddress.ipVersion.IPV4;
import static com.predic8.membrane.core.interceptor.acl2.IpAddress.ipVersion.IPV6;
import static java.net.InetAddress.getByName;

public final class IpAddress {

    private final InetAddress address;
    private final ipVersion version;

    private String hostname = "";

    private IpAddress(InetAddress address) {
        this.address = Objects.requireNonNull(address, "address");
        if (address instanceof Inet4Address) {
            this.version = IPV4;
        } else if (address instanceof Inet6Address) {
            this.version = IPV6;
        } else {
            // Should never happen
            throw new IllegalArgumentException("Unsupported InetAddress type: " + address.getClass().getName());
        }
    }

    public static IpAddress of(InetAddress address) {
        return new IpAddress(address);
    }

    public static IpAddress parse(String raw) {
        Objects.requireNonNull(raw, "raw");

        try {
            return new IpAddress(getByName(removeBracketsIfPresent(raw.trim())));
        } catch (UnknownHostException e) {
            // Incoming values should be valid
            throw new IllegalArgumentException("Invalid IP address: " + raw, e);
        }
    }

    static String removeBracketsIfPresent(String s) {
        if (s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    public ipVersion version() {
        return version;
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = (hostname == null) ? "" : hostname;
    }

    public enum ipVersion {
        IPV4, IPV6
    }
}
