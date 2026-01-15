/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.acl.targets;

import com.predic8.membrane.core.interceptor.acl.IpAddress;
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

    @ParameterizedTest(name = "tryCreate rejects ip-like: {0}")
    @ValueSource(strings = {
            "1.2.3.4",
            "1.2.3.4/24",
            "999.1.1.1/33",
            "2001:db8::1",
            "2001:db8::1/64",
            "[2001:db8::1]/64"
    })
    void tryCreate_rejects_ip_like_inputs(String s) {
        assertTrue(HostnameTarget.tryCreate(s).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new HostnameTarget(s));
    }
}
