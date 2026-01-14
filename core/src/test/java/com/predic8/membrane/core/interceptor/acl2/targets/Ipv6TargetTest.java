package com.predic8.membrane.core.interceptor.acl2.targets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.interceptor.acl2.IpAddress.parse;
import static com.predic8.membrane.core.interceptor.acl2.targets.Ipv6Target.tryCreate;
import static org.junit.jupiter.api.Assertions.*;

class Ipv6TargetTest {

    @ParameterizedTest(name = "accepts: \"{0}\"")
    @ValueSource(strings = {
            "::1",
            "::",
            "2001:db8::1",
            "2001:db8::/64",
            "2001:db8::1/128",
            "::/0",
            "[2001:db8::1]",
            "[2001:db8::1]/64",
            "[2001:db8::1]/64",
            "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff/128"
    })
    void acceptsValid(String input) {
        assertTrue(tryCreate(input).isPresent());
    }

    @ParameterizedTest(name = "denies: \"{0}\"")
    @ValueSource(strings = {
            "",
            "   ",
            "not-an-ip",
            "1.2.3.4",
            "1.2.3.4/24",
            "2001:db8::1/129",
            "2001:db8::1/-1",
            "2001:db8::1/",
            "2001:db8::1//64",
            "2001:db8::1/ 64",
            "2001:db8::1/08",
            "[2001:db8::1]/200",
            "[2001:db8::1",
            "2001:db8::1]"
    })
    void deniesInvalid(String input) {
        assertFalse(tryCreate(input).isPresent());
    }

    @Test
    void peerMatches_default_cidr_is_128() {
        Ipv6Target t = new Ipv6Target("2001:db8::1");
        assertTrue(t.peerMatches(parse("2001:db8::1")));
        assertFalse(t.peerMatches(parse("2001:db8::2")));
    }

    @Test
    void peerMatches_128_exact_match_only() {
        Ipv6Target t = new Ipv6Target("2001:db8::1/128");
        assertTrue(t.peerMatches(parse("2001:db8::1")));
        assertFalse(t.peerMatches(parse("2001:db8::2")));
    }

    @Test
    void peerMatches_64() {
        Ipv6Target t = new Ipv6Target("2001:db8::/64");
        assertTrue(t.peerMatches(parse("2001:db8::1")));
        assertTrue(t.peerMatches(parse("2001:db8::ffff")));
        assertFalse(t.peerMatches(parse("2001:db9::1")));
    }

    @Test
    void peerMatches_0_is_all() {
        Ipv6Target t = new Ipv6Target("::/0");
        assertTrue(t.peerMatches(parse("::1")));
        assertTrue(t.peerMatches(parse("2001:db8::1")));
        assertTrue(t.peerMatches(parse("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")));
    }

    @Test
    void peerMatches_127_includes_two_addresses() {
        Ipv6Target t = new Ipv6Target("2001:db8::/127");
        assertTrue(t.peerMatches(parse("2001:db8::")));
        assertTrue(t.peerMatches(parse("2001:db8::1")));
        assertFalse(t.peerMatches(parse("2001:db8::2")));
    }

    @Test
    void peerMatches_rejects_ipv4_peers() {
        Ipv6Target t = new Ipv6Target("::/0");
        assertFalse(t.peerMatches(parse("1.2.3.4")));
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
        assertTrue(tryCreate("2001:db8::1").isPresent());
        assertTrue(tryCreate("2001:db8::1/64").isPresent());
        assertTrue(tryCreate("[2001:db8::1]/64").isPresent());

        assertFalse(tryCreate("1.2.3.4").isPresent());
        assertFalse(tryCreate("1.2.3.4/24").isPresent());
        assertFalse(tryCreate("2001:db8::1/129").isPresent());
        assertFalse(tryCreate("not-an-ip").isPresent());
    }

    @Test
    void ctor_allows_brackets() {
        Ipv6Target t = new Ipv6Target("[2001:db8::]/64");
        assertTrue(t.peerMatches(parse("2001:db8::1")));
        assertFalse(t.peerMatches(parse("2001:db9::1")));
    }
}
