package com.predic8.membrane.core.interceptor.acl2.address;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.Inet4Address;

import static org.junit.jupiter.api.Assertions.*;

class Ipv4AddressTest {

    @Test
    void parse_null_or_blank_is_empty() {
        assertTrue(Ipv4Address.parse(null).isEmpty());
        assertTrue(Ipv4Address.parse("").isEmpty());
        assertTrue(Ipv4Address.parse("   ").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0",
            "1.2.3.4",
            "10.0.0.1",
            "127.0.0.1",
            "192.168.1.10",
            "255.255.255.255",
            " 203.0.113.7 ",
    })
    void parse_valid_ipv4(String s) {
        var ip = Ipv4Address.parse(s).orElseThrow();
        assertEquals(IpAddress.ipVersion.IPV4, ip.version());
        assertInstanceOf(Inet4Address.class, ip.getAddress());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "256.0.0.1",
            "999.1.1.1",
            "1.2.3",
            "1.2.3.4.5",
            "1.2.3.-1",
            "01.2.3.4",
            "1.02.3.4",
            "1.2.003.4",
            "1.2.3.04",
            "1..3.4",
            "1.2.3.",
            ".1.2.3.4",
            " 1.2.3.4 x",
            "1.2.3.4/24"
    })
    void parse_invalid_ipv4(String s) {
        assertTrue(Ipv4Address.parse(s).isEmpty(), () -> "Should be invalid: " + s);
    }
}
