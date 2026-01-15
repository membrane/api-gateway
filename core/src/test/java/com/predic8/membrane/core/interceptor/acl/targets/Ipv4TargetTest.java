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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.interceptor.acl.IpAddress.parse;
import static com.predic8.membrane.core.interceptor.acl.targets.Ipv4Target.tryCreate;
import static org.junit.jupiter.api.Assertions.*;

class Ipv4TargetTest {

    @ParameterizedTest(name = "accepts: \"{0}\"")
    @ValueSource(strings = {
            "192.168.0.1",
            "0.0.0.0",
            "255.255.255.255",
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.0.1/0",
            "192.168.0.1/24",
            "192.168.0.1/32",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/24",
            "192.168.0.1",
            "10.0.0.0/8"
    })
    void acceptsValid(String input) {
        assertTrue(tryCreate(input).isPresent());
    }

    @ParameterizedTest(name = "denies: \"{0}\"")
    @ValueSource(strings = {
            "192.168.0.1/33",
            "192.168.0.1/128",
            "192.168.0.1/-1",
            "192.168.0.1/",
            "192.168.0.1//24",
            "192.168.0.1/ 24",
            "192.168.0.321",
            "256.0.0.1",
            "-1.0.0.1",
            "192.168.0",
            "192.168.0.0.1",
            "192.168..1",
            "192.168.0.1.",
            ".192.168.0.1",
            "192.168.0.one",
            "192.168.0.1/abc",
            "abc"
    })
    void deniesInvalid(String input) {
        assertFalse(tryCreate(input).isPresent());
    }

    @Test
    void ctor_parses_address_and_default_cidr_32() {
        Ipv4Target t = new Ipv4Target("203.0.113.7");
        assertTrue(t.peerMatches(parse("203.0.113.7")));
        assertFalse(t.peerMatches(parse("203.0.113.8")));
    }

    @Test
    void peerMatches_prefix_32_exact_match_only() {
        Ipv4Target t = new Ipv4Target("192.168.1.10/32");
        assertTrue(t.peerMatches(parse("192.168.1.10")));
        assertFalse(t.peerMatches(parse("192.168.1.11")));
    }

    @Test
    void peerMatches_prefix_24_includes_full_range() {
        Ipv4Target t = new Ipv4Target("192.168.1.0/24");
        assertTrue(t.peerMatches(parse("192.168.1.0")));
        assertTrue(t.peerMatches(parse("192.168.1.1")));
        assertTrue(t.peerMatches(parse("192.168.1.255")));
        assertFalse(t.peerMatches(parse("192.168.2.1")));
    }

    @Test
    void peerMatches_prefix_31_includes_two_addresses() {
        Ipv4Target t = new Ipv4Target("192.168.1.10/31");
        assertTrue(t.peerMatches(parse("192.168.1.10")));
        assertTrue(t.peerMatches(parse("192.168.1.11")));
        assertFalse(t.peerMatches(parse("192.168.1.12")));
    }

    @Test
    void peerMatches_prefix_1_splits_ipv4_space_in_half() {
        Ipv4Target t = new Ipv4Target("128.0.0.0/1");
        assertTrue(t.peerMatches(parse("200.1.1.1")));
        assertFalse(t.peerMatches(parse("10.0.0.1")));
    }

    @Test
    void peerMatches_prefix_0_matches_all() {
        Ipv4Target t = new Ipv4Target("0.0.0.0/0");
        assertTrue(t.peerMatches(parse("1.2.3.4")));
        assertTrue(t.peerMatches(parse("255.255.255.255")));
        assertTrue(t.peerMatches(parse("0.0.0.0")));
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
    void accepts_ipv4_with_or_without_cidr() {
        assertTrue(tryCreate("1.2.3.4").isPresent());
        assertTrue(tryCreate("1.2.3.4/24").isPresent());
    }
}
