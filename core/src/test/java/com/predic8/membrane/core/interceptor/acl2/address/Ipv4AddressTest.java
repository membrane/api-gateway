package com.predic8.membrane.core.interceptor.acl2.address;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Ipv4AddressTest {

    @Test
    void matches_exact_32() {
        Ipv4Address net = Ipv4Address.parse("192.168.1.10/32").orElseThrow();
        assertTrue(net.matches(Ipv4Address.parse("192.168.1.10").orElseThrow()));
        assertFalse(net.matches(Ipv4Address.parse("192.168.1.11").orElseThrow()));
    }

    @Test
    void matches_24() {
        Ipv4Address net = Ipv4Address.parse("192.168.1.0/24").orElseThrow();
        assertTrue(net.matches(Ipv4Address.parse("192.168.1.1").orElseThrow()));
        assertTrue(net.matches(Ipv4Address.parse("192.168.1.255").orElseThrow()));
        assertFalse(net.matches(Ipv4Address.parse("192.168.2.1").orElseThrow()));
    }

    @Test
    void matches_8() {
        Ipv4Address net = Ipv4Address.parse("10.0.0.0/8").orElseThrow();
        assertTrue(net.matches(Ipv4Address.parse("10.123.45.67").orElseThrow()));
        assertFalse(net.matches(Ipv4Address.parse("11.0.0.1").orElseThrow()));
    }

    @Test
    void matches_0_is_all() {
        Ipv4Address net = Ipv4Address.parse("0.0.0.0/0").orElseThrow();
        assertTrue(net.matches(Ipv4Address.parse("1.2.3.4").orElseThrow()));
        assertTrue(net.matches(Ipv4Address.parse("255.255.255.255").orElseThrow()));
    }

    @Test
    void default_cidr_is_32() {
        Ipv4Address ip = Ipv4Address.parse("203.0.113.7").orElseThrow();
        assertEquals(32, ip.getCidr());
        assertTrue(ip.matches(Ipv4Address.parse("203.0.113.7").orElseThrow()));
        assertFalse(ip.matches(Ipv4Address.parse("203.0.113.8").orElseThrow()));
    }
}
