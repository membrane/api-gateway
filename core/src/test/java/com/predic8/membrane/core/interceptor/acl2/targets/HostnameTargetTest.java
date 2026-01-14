package com.predic8.membrane.core.interceptor.acl2.targets;

import com.predic8.membrane.core.interceptor.acl2.IpAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HostnameTargetTest {

    @Test
    void ctor_rejects_invalid_regex() {
        assertThrows(IllegalArgumentException.class, () -> new HostnameTarget("["));
        assertThrows(IllegalArgumentException.class, () -> new HostnameTarget("(*"));
    }

    @Test
    void peerMatches_requires_hostname_to_be_set() {
        HostnameTarget t = new HostnameTarget("^example\\.com$");
        IpAddress ip = IpAddress.parse("127.0.0.1");
        assertFalse(t.peerMatches(ip));
    }

    @Test
    void peerMatches_exact() {
        HostnameTarget t = new HostnameTarget("^example\\.com$");

        IpAddress ip = IpAddress.parse("127.0.0.1");
        ip.setHostname("example.com");
        assertTrue(t.peerMatches(ip));

        ip.setHostname("sub.example.com");
        assertFalse(t.peerMatches(ip));
    }

    @Test
    void peerMatches_subdomain_regex() {
        HostnameTarget t = new HostnameTarget("^([a-z0-9-]+\\.)*example\\.com$");

        IpAddress ip = IpAddress.parse("127.0.0.1");

        ip.setHostname("example.com");
        assertTrue(t.peerMatches(ip));

        ip.setHostname("a.example.com");
        assertTrue(t.peerMatches(ip));

        ip.setHostname("a.b.example.com");
        assertTrue(t.peerMatches(ip));

        ip.setHostname("example.org");
        assertFalse(t.peerMatches(ip));
    }

    @ParameterizedTest
    @ValueSource(strings = {"^example\\.com$", ".*", "^[a-z]+\\.(com|net)$"})
    void tryCreate_accepts_valid_regexes(String regex) {
        Optional<Target> t = HostnameTarget.tryCreate(regex);
        assertTrue(t.isPresent());
        assertInstanceOf(HostnameTarget.class, t.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[", "(*"})
    void tryCreate_returns_empty_for_invalid(String regex) {
        assertTrue(HostnameTarget.tryCreate(regex).isEmpty());
    }
}
