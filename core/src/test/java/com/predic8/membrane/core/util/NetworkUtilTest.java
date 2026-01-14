/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.ServerSocket;

import static com.predic8.membrane.core.util.NetworkUtil.*;
import static java.lang.Long.parseUnsignedLong;
import static java.net.InetAddress.getByName;
import static org.junit.jupiter.api.Assertions.*;

class NetworkUtilTest {
    @Test
    void testGetRandomPortEqualAbove3000() throws Exception {
        assertTrue(getFreePortEqualAbove(3000) >= 3000);
    }

    @Test
    void testFailToGetPortAbove65534() {
        try (ServerSocket ignored = new ServerSocket(65535)) {
            assertThrows(IOException.class, () -> getFreePortEqualAbove(65535));
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind port 65535.", e);
        }
    }

    @Test
    void removeBracketsIfPresent_removes_and_trims() {
        assertEquals("2001:db8::1", removeBracketsIfPresent("[2001:db8::1]"));
        assertEquals("2001:db8::1", removeBracketsIfPresent("[ 2001:db8::1 ]"));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.0.0,         0x00000000",
            "255.255.255.255, 0xFFFFFFFF",
            "192.168.1.10,    0xC0A8010A",
            "203.0.113.7,     0xCB007107",
            "10.0.0.1,        0x0A000001"
    })
    void parseDottedQuadToInt_parses_big_endian(String ip, String expectedHex) {
        int expected = (int) parseUnsignedLong(expectedHex.substring(2), 16);
        assertEquals(expected, parseDottedQuadToInt(ip));
    }

    @Test
    void toInet4Address_handles_high_bit_addresses() {
        String ip = "200.1.1.1";
        assertEquals(ip, toInet4Address(parseDottedQuadToInt(ip)).getHostAddress());
    }

    @ParameterizedTest(name = "IPv4: {0} vs {1} /{2} -> {3}")
    @CsvSource({
            "10.0.0.0,            10.123.45.67,        8,   true",
            "10.0.0.0,            11.0.0.1,            8,   false",

            "192.168.1.10,        192.168.1.10,        32,  true",
            "192.168.1.10,        192.168.1.11,        32,  false",

            "192.168.1.10,        192.168.1.11,        31,  true",
            "192.168.1.10,        192.168.1.12,        31,  false",

            "0.0.0.0,             255.255.255.255,     0,   true"
    })
    void matchesPrefix_ipv4(String a, String b, int prefix, boolean expected) throws Exception {
        assertEquals(
                expected,
                matchesPrefix(getByName(a).getAddress(), getByName(b).getAddress(), prefix)
        );
    }

    @ParameterizedTest(name = "IPv6: {0} vs {1} /{2} -> {3}")
    @CsvSource({
            "'2001:db8::',        '2001:db8::1',        64,  true",
            "'2001:db8::',        '2001:db9::1',        64,  false",

            "'2001:db8::1',       '2001:db8::1',        128, true",
            "'2001:db8::1',       '2001:db8::2',        128, false",

            "'2001:db8::',        '2001:db8::1',        127, true",
            "'2001:db8::',        '2001:db8::2',        127, false",

            "'::',               'ffff::',             0,   true"
    })
    void matchesPrefix_ipv6(String a, String b, int prefix, boolean expected) throws Exception {
        assertEquals(
                expected,
                matchesPrefix(getByName(a).getAddress(), getByName(b).getAddress(), prefix)
        );
    }

    @Test
    void matchesPrefix_ipv6_partialBit_65() throws Exception {
        byte[] a = getByName("2001:db8:0:0::").getAddress();
        byte[] b = getByName("2001:db8:0:0:8000::").getAddress();
        assertTrue(matchesPrefix(a, b, 64));
        assertFalse(matchesPrefix(a, b, 65));
    }

    @Test
    void matchesPrefix_lengthMismatch_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> matchesPrefix(new byte[4], new byte[16], 8));
    }
}
