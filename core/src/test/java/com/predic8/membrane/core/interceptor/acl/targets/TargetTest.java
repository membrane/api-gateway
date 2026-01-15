package com.predic8.membrane.core.interceptor.acl.targets;

import com.predic8.membrane.core.interceptor.acl.IpAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TargetTest {

    @ParameterizedTest(name = "byMatch(\"{0}\") -> Ipv4Target")
    @ValueSource(strings = {
            "192.168.0.1",
            "10.0.0.0/8",
            "0.0.0.0/0",
            "203.0.113.7/32"
    })
    void byMatch_creates_ipv4_target(String raw) {
        Target t = Target.byMatch(raw);
        assertInstanceOf(Ipv4Target.class, t);

        // Sanity: should match itself
        assertTrue(t.peerMatches(IpAddress.parse(raw.trim().split("/")[0])));
    }

    @ParameterizedTest(name = "byMatch(\"{0}\") -> Ipv6Target")
    @ValueSource(strings = {
            "2001:db8::1",
            "2001:db8::/64",
            "::/0",
            "[2001:db8::]/64",
            "[2001:db8::1]"
    })
    void byMatch_creates_ipv6_target(String raw) {
        Target t = Target.byMatch(raw);
        assertInstanceOf(Ipv6Target.class, t);
    }

    @ParameterizedTest(name = "byMatch(\"{0}\") -> HostnameTarget")
    @ValueSource(strings = {
            "^example\\.com$",
            "^([a-z0-9-]+\\.)*example\\.com$",
            ".*"
    })
    void byMatch_creates_hostname_target(String raw) {
        Target t = Target.byMatch(raw);
        assertInstanceOf(HostnameTarget.class, t);

        IpAddress ip = IpAddress.parse("127.0.0.1");
        ip.setHostname("example.com");
        assertTrue(t.peerMatches(ip));
    }

    @Test
    void byMatch_rejects_invalid_ipv4_ipv6_and_invalid_hostname_regex() {
        assertThrows(IllegalArgumentException.class, () -> Target.byMatch("999.1.1.1/33"));
        assertThrows(IllegalArgumentException.class, () -> Target.byMatch("2001:db8::1/129"));
        assertThrows(IllegalArgumentException.class, () -> Target.byMatch("["));
    }

}
