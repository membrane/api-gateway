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
        assertTrue(list.matches("localhost"));
        assertTrue(list.matches("LOCALHOST"));
        assertFalse(list.matches("localhost2"));
        assertFalse(list.matches("notlocalhost"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", ".example.com", "*.example.com", " Example.COM. "})
    void domainMatchesItselfAndSubdomains(String entry) {
        final var list = NoProxyList.parse(entry);
        assertTrue(list.matches("example.com"));
        assertTrue(list.matches("api.example.com"));
        assertTrue(list.matches("a.b.example.com"));
        assertTrue(list.matches("api.example.com."));
        assertFalse(list.matches("badexample.com"));
        assertFalse(list.matches("example.org"));
    }

    @Test
    void severalEntries() {
        final var list = NoProxyList.parse("localhost, ,.internal.example.com,10.0.0.0/8");
        assertTrue(list.matches("localhost"));
        assertTrue(list.matches("orders.internal.example.com"));
        assertTrue(list.matches("10.1.2.3"));
        assertFalse(list.matches("www.example.com"));
        assertFalse(list.matches("192.168.0.1"));
    }

    @Test
    void wildcardMatchesEverything() {
        final var list = NoProxyList.parse("*");
        assertTrue(list.matches("example.com"));
        assertTrue(list.matches("10.0.0.1"));
    }

    @Test
    void emptyListMatchesNothing() {
        assertFalse(NoProxyList.parse("").matches("localhost"));
    }

    @Test
    void ipv4() {
        final var list = NoProxyList.parse("127.0.0.1, 192.168.0.0/16");
        assertTrue(list.matches("127.0.0.1"));
        assertTrue(list.matches("192.168.10.20"));
        assertFalse(list.matches("127.0.0.2"));
        assertFalse(list.matches("192.169.0.1"));
    }

    @Test
    void ipv6() {
        final var list = NoProxyList.parse("::1, 2001:db8::/32");
        assertTrue(list.matches("::1"));
        assertTrue(list.matches("[::1]"));
        assertTrue(list.matches("2001:db8:1::5"));
        assertFalse(list.matches("2001:db9::1"));
        assertFalse(list.matches("127.0.0.1"));
    }

    @Test
    void ipEntryDoesNotMatchHostNames() {
        assertFalse(NoProxyList.parse("127.0.0.1").matches("localhost"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"300.1.1.1", "10.0.0.0/33", "host:8080", "api.*.example.com", "*foo", ".", "*."})
    void invalidEntriesAreRejected(String entry) {
        assertThrows(ConfigurationException.class, () -> NoProxyList.parse(entry));
    }
}
