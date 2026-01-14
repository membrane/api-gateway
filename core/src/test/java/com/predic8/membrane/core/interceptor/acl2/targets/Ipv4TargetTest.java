package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.address.Ipv4Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class Ipv4TargetTest {

    @Test
    void ctor_parses_address_and_default_cidr_32() {
        Ipv4Target t = new Ipv4Target("203.0.113.7");
        assertEquals("203.0.113.7", t.getTarget().getHostAddress());
        assertTrue(t.peerMatches(Ipv4Address.parse("203.0.113.7").orElseThrow()));
        assertFalse(t.peerMatches(Ipv4Address.parse("203.0.113.8").orElseThrow()));
    }

    @Test
    void ctor_trims_input() {
        Ipv4Target t = new Ipv4Target(" 10.0.0.0/8 ");
        assertEquals("10.0.0.0", t.getTarget().getHostAddress());
        assertTrue(t.peerMatches(Ipv4Address.parse("10.123.45.67").orElseThrow()));
        assertFalse(t.peerMatches(Ipv4Address.parse("11.0.0.1").orElseThrow()));
    }

    @Test
    void peerMatches_prefix_32_exact_match_only() {
        Ipv4Target t = new Ipv4Target("192.168.1.10/32");
        assertTrue(t.peerMatches(Ipv4Address.parse("192.168.1.10").orElseThrow()));
        assertFalse(t.peerMatches(Ipv4Address.parse("192.168.1.11").orElseThrow()));
    }

    @Test
    void peerMatches_prefix_24_includes_full_range() {
        Ipv4Target t = new Ipv4Target("192.168.1.0/24");
        assertTrue(t.peerMatches(Ipv4Address.parse("192.168.1.0").orElseThrow()));
        assertTrue(t.peerMatches(Ipv4Address.parse("192.168.1.1").orElseThrow()));
        assertTrue(t.peerMatches(Ipv4Address.parse("192.168.1.255").orElseThrow()));
        assertFalse(t.peerMatches(Ipv4Address.parse("192.168.2.1").orElseThrow()));
    }

    @Test
    void peerMatches_prefix_31_includes_two_addresses() {
        Ipv4Target t = new Ipv4Target("192.168.1.10/31");
        assertTrue(t.peerMatches(Ipv4Address.parse("192.168.1.10").orElseThrow()));
        assertTrue(t.peerMatches(Ipv4Address.parse("192.168.1.11").orElseThrow()));
        assertFalse(t.peerMatches(Ipv4Address.parse("192.168.1.12").orElseThrow()));
    }

    @Test
    void peerMatches_prefix_1_splits_ipv4_space_in_half() {
        Ipv4Target t = new Ipv4Target("128.0.0.0/1");
        assertTrue(t.peerMatches(Ipv4Address.parse("200.1.1.1").orElseThrow()));
        assertFalse(t.peerMatches(Ipv4Address.parse("10.0.0.1").orElseThrow()));
    }

    @Test
    void peerMatches_prefix_0_matches_all() {
        Ipv4Target t = new Ipv4Target("0.0.0.0/0");
        assertTrue(t.peerMatches(Ipv4Address.parse("1.2.3.4").orElseThrow()));
        assertTrue(t.peerMatches(Ipv4Address.parse("255.255.255.255").orElseThrow()));
        assertTrue(t.peerMatches(Ipv4Address.parse("0.0.0.0").orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "999.1.1.1",
            "1.2.3",
            "1.2.3.4/33",
            "1.2.3.4/-1",
            "1.2.3.4/",
            "1.2.3.4/ 8",
            "1.2.3.4/08",
            "01.2.3.4/24"
    })
    void ctor_rejects_invalid_inputs(String raw) {
        assertThrows(IllegalArgumentException.class, () -> new Ipv4Target(raw));
    }

    @Test
    void accepts_only_plain_ipv4_not_cidr() {
        assertTrue(Ipv4Target.accepts("1.2.3.4"));
        assertFalse(Ipv4Target.accepts("1.2.3.4/24"));
    }
}
