package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.address.Ipv4Address;
import com.predic8.membrane.core.interceptor.acl2.address.Ipv6Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class Ipv6TargetTest {

    @Test
    void peerMatches_default_cidr_is_128() {
        Ipv6Target t = new Ipv6Target("2001:db8::1");
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::1").orElseThrow()));
        assertFalse(t.peerMatches(Ipv6Address.parse("2001:db8::2").orElseThrow()));
    }

    @Test
    void peerMatches_128_exact_match_only() {
        Ipv6Target t = new Ipv6Target("2001:db8::1/128");
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::1").orElseThrow()));
        assertFalse(t.peerMatches(Ipv6Address.parse("2001:db8::2").orElseThrow()));
    }

    @Test
    void peerMatches_64() {
        Ipv6Target t = new Ipv6Target("2001:db8::/64");
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::1").orElseThrow()));
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::ffff").orElseThrow()));
        assertFalse(t.peerMatches(Ipv6Address.parse("2001:db9::1").orElseThrow()));
    }

    @Test
    void peerMatches_0_is_all() {
        Ipv6Target t = new Ipv6Target("::/0");
        assertTrue(t.peerMatches(Ipv6Address.parse("::1").orElseThrow()));
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::1").orElseThrow()));
        assertTrue(t.peerMatches(Ipv6Address.parse("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff").orElseThrow()));
    }

    @Test
    void peerMatches_127_includes_two_addresses() {
        Ipv6Target t = new Ipv6Target("2001:db8::/127");
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::").orElseThrow()));
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::1").orElseThrow()));
        assertFalse(t.peerMatches(Ipv6Address.parse("2001:db8::2").orElseThrow()));
    }

    @Test
    void peerMatches_rejects_ipv4_peers() {
        Ipv6Target t = new Ipv6Target("::/0");
        assertFalse(t.peerMatches(Ipv4Address.parse("1.2.3.4").orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not-an-ip",
            "1.2.3.4/64",
            "2001:db8::1/129",
            "2001:db8::1/-1",
            "2001:db8::1/",
            "2001:db8::1/08"
    })
    void ctor_rejects_invalid_inputs(String raw) {
        assertThrows(IllegalArgumentException.class, () -> new Ipv6Target(raw));
    }

    @Test
    void accepts_ipv6_with_or_without_cidr() {
        assertTrue(Ipv6Target.accepts("2001:db8::1"));
        assertTrue(Ipv6Target.accepts("2001:db8::1/64"));
        assertTrue(Ipv6Target.accepts("[2001:db8::1]/64"));

        assertFalse(Ipv6Target.accepts("1.2.3.4"));
        assertFalse(Ipv6Target.accepts("1.2.3.4/24"));
        assertFalse(Ipv6Target.accepts("2001:db8::1/129"));
        assertFalse(Ipv6Target.accepts("not-an-ip"));
    }


    @Test
    void ctor_allows_brackets() {
        Ipv6Target t = new Ipv6Target("[2001:db8::]/64");
        assertTrue(t.peerMatches(Ipv6Address.parse("2001:db8::1").orElseThrow()));
        assertFalse(t.peerMatches(Ipv6Address.parse("2001:db9::1").orElseThrow()));
    }
}
