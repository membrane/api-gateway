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

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NoProxyListTest {

    @Test
    void exactHost() {
        final var list = NoProxyList.parse("localhost");
        assertTrue(list.matches("localhost", 80));
        assertTrue(list.matches("LOCALHOST", 80));
        assertFalse(list.matches("localhost2", 80));
        assertFalse(list.matches("notlocalhost", 80));
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", ".example.com", "*.example.com", " Example.COM. "})
    void domainMatchesItselfAndSubdomains(String entry) {
        final var list = NoProxyList.parse(entry);
        assertTrue(list.matches("example.com", 80));
        assertTrue(list.matches("api.example.com", 80));
        assertTrue(list.matches("a.b.example.com", 80));
        assertTrue(list.matches("api.example.com.", 80));
        assertFalse(list.matches("badexample.com", 80));
        assertFalse(list.matches("example.org", 80));
    }

    @Test
    void severalEntries() {
        final var list = NoProxyList.parse("localhost, ,.internal.example.com,10.0.0.0/8");
        assertTrue(list.matches("localhost", 80));
        assertTrue(list.matches("orders.internal.example.com", 80));
        assertTrue(list.matches("10.1.2.3", 80));
        assertFalse(list.matches("www.example.com", 80));
        assertFalse(list.matches("192.168.0.1", 80));
    }

    @Test
    void wildcardMatchesEverything() {
        final var list = NoProxyList.parse("*");
        assertTrue(list.matches("example.com", 80));
        assertTrue(list.matches("10.0.0.1", 80));
    }

    @Test
    void emptyListMatchesNothing() {
        assertFalse(NoProxyList.parse("").matches("localhost", 80));
    }

    @Test
    void ipv4() {
        final var list = NoProxyList.parse("127.0.0.1, 192.168.0.0/16");
        assertTrue(list.matches("127.0.0.1", 80));
        assertTrue(list.matches("192.168.10.20", 80));
        assertFalse(list.matches("127.0.0.2", 80));
        assertFalse(list.matches("192.169.0.1", 80));
    }

    @Test
    void ipv6() {
        final var list = NoProxyList.parse("::1, 2001:db8::/32");
        assertTrue(list.matches("::1", 80));
        assertTrue(list.matches("[::1]", 80));
        assertTrue(list.matches("2001:db8:1::5", 80));
        assertFalse(list.matches("2001:db9::1", 80));
        assertFalse(list.matches("127.0.0.1", 80));
    }

    @Test
    void ipv4EntryWithTrailingDot() {
        final var list = NoProxyList.parse("127.0.0.1.");
        assertTrue(list.matches("127.0.0.1", 80));
        assertTrue(list.matches("127.0.0.1.", 80));
    }

    @Test
    void ipEntryDoesNotMatchHostNames() {
        assertFalse(NoProxyList.parse("127.0.0.1").matches("localhost", 80));
    }

    @Test
    void domainWithPortMatchesOnlyThatPort() {
        final var list = NoProxyList.parse("internal.example.com:8080");
        assertTrue(list.matches("internal.example.com", 8080));
        assertTrue(list.matches("orders.internal.example.com", 8080));
        assertFalse(list.matches("internal.example.com", 80));
    }

    @Test
    void ipWithPortMatchesOnlyThatPort() {
        final var list = NoProxyList.parse("127.0.0.1:8080, 10.0.0.0/8:9000, [::1]:8080");
        assertTrue(list.matches("127.0.0.1", 8080));
        assertFalse(list.matches("127.0.0.1", 80));
        assertTrue(list.matches("10.1.2.3", 9000));
        assertFalse(list.matches("10.1.2.3", 8080));
        assertTrue(list.matches("::1", 8080));
        assertFalse(list.matches("::1", 80));
    }

    @Test
    void entriesWithAndWithoutPortCombine() {
        final var list = NoProxyList.parse("localhost, internal.example.com:8080");
        assertTrue(list.matches("localhost", 1234));
        assertTrue(list.matches("internal.example.com", 8080));
        assertFalse(list.matches("internal.example.com", 443));
    }

    @ParameterizedTest
    @ValueSource(strings = {"300.1.1.1", "10.0.0.0/33", "10.0.0.0/8.", "2001:db8::/32.", "host:", "host:abc", "host:0", "host:65536", "[::1]:x", "api.*.example.com", "*foo", ".", "*."})
    void invalidEntriesAreRejected(String entry) {
        assertThrows(ConfigurationException.class, () -> NoProxyList.parse(entry));
    }
}
