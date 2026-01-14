package com.predic8.membrane.core.interceptor.acl2.address;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.Inet6Address;

import static org.junit.jupiter.api.Assertions.*;

class Ipv6AddressTest {

    @Test
    void parse_null_or_blank_is_empty() {
        assertTrue(Ipv6Address.parse(null).isEmpty());
        assertTrue(Ipv6Address.parse("").isEmpty());
        assertTrue(Ipv6Address.parse("   ").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::",
            "::1",
            "2001:db8::1",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "[2001:db8::1]"
    })
    void parse_valid_ipv6(String s) {
        var ip = Ipv6Address.parse(s).orElseThrow();
        assertEquals(IpAddress.ipVersion.IPV6, ip.version());
        assertInstanceOf(Inet6Address.class, ip.getAddress());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2001:db8::1/64",
            "12345::",
            "gggg::1",
            ":::1",
            "1.2.3.4",
            " [2001:db8::1] x "
    })
    void parse_invalid_ipv6(String s) {
        assertTrue(Ipv6Address.parse(s).isEmpty(), () -> "Should be invalid: " + s);
    }

    @ParameterizedTest
    @CsvSource({
            "'[2001:db8::1]',      '2001:db8::1'",
            "'2001:db8::1',        '2001:db8::1'",
            "'[]',                 ''",
            "'[',                  '['",
            "']',                  ']'",
            "'[2001:db8::1',       '[2001:db8::1'",
            "'2001:db8::1]',       '2001:db8::1]'",
            "'[ 2001:db8::1 ]',    '2001:db8::1'",
            "'[::]',               '::'",
            "'[::1]',              '::1'",
    })
    void removeOptionalBrackets(String in, String expected) {
        assertEquals(expected, Ipv6Address.removeBracketsIfPresent(in));
    }
}
