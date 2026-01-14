package com.predic8.membrane.core.interceptor.acl2;

import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.predic8.membrane.core.interceptor.acl2.IpAddress.ipVersion.IPV4;
import static com.predic8.membrane.core.interceptor.acl2.IpAddress.ipVersion.IPV6;
import static com.predic8.membrane.core.interceptor.acl2.IpAddress.*;
import static java.net.InetAddress.getByName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IpAddressTest {

    @Test
    void parse_ipv4_sets_version_and_address() {
        IpAddress ip = parse("203.0.113.7");
        assertEquals(IPV4, ip.version());
        assertEquals("203.0.113.7", ip.getInetAddress().getHostAddress());
        assertInstanceOf(Inet4Address.class, ip.getInetAddress());
    }

    @Test
    void parse_ipv6_sets_version_and_address() {
        IpAddress ip = parse("2001:db8::1");
        assertEquals(IPV6, ip.version());
        assertEquals("2001:db8:0:0:0:0:0:1", ip.getInetAddress().getHostAddress());
        assertInstanceOf(Inet6Address.class, ip.getInetAddress());
    }

    @Test
    void parse_trims_input() {
        IpAddress ip = parse("  10.0.0.1  ");
        assertEquals(IPV4, ip.version());
        assertEquals("10.0.0.1", ip.getInetAddress().getHostAddress());
    }

    @Test
    void parse_allows_brackets_for_ipv6() {
        IpAddress ip = parse("[2001:db8::1]");
        assertEquals(IPV6, ip.version());
        assertInstanceOf(Inet6Address.class, ip.getInetAddress());
    }

    @Test
    void removeBracketsIfPresent_removes_and_trims() {
        assertEquals("2001:db8::1", removeBracketsIfPresent("[2001:db8::1]"));
        assertEquals("2001:db8::1", removeBracketsIfPresent("[ 2001:db8::1 ]"));
    }

    @Test
    void of_infers_version_ipv4() throws UnknownHostException {
        InetAddress inet = getByName("127.0.0.1");
        IpAddress ip = of(inet);
        assertEquals(IPV4, ip.version());
        assertEquals(inet, ip.getInetAddress());
    }

    @Test
    void of_infers_version_ipv6() throws UnknownHostException {
        InetAddress inet = getByName("::1");
        IpAddress ip = of(inet);
        assertEquals(IPV6, ip.version());
        assertEquals(inet, ip.getInetAddress());
    }

    @Test
    void hostname_defaults_to_empty_and_is_settable() {
        IpAddress ip = parse("127.0.0.1");
        assertEquals("", ip.getHostname());

        ip.setHostname("localhost");
        assertEquals("localhost", ip.getHostname());

        ip.setHostname(null);
        assertEquals("", ip.getHostname());
    }
}
