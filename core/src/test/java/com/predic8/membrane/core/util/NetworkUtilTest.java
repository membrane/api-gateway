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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import static com.predic8.membrane.core.util.NetworkUtil.*;
import static java.lang.Long.parseUnsignedLong;
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

    @ParameterizedTest(name = "maskOf({0}) == {1}")
    @CsvSource({
            "0,   0x00000000",
            "1,   0x80000000",
            "8,   0xFF000000",
            "16,  0xFFFF0000",
            "24,  0xFFFFFF00",
            "31,  0xFFFFFFFE",
            "32,  0xFFFFFFFF",
            "33,  0xFFFFFFFF",
            "-1,  0x00000000"
    })
    void maskOf_builds_expected_masks(int prefix, String expectedHex) {
        assertEquals((int) parseUnsignedLong(expectedHex.substring(2), 16), maskOf(prefix));
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
    void bytesToInt_matches_parseDottedQuadToInt() throws UnknownHostException {
        String ip = "192.168.1.10";
        byte[] bytes = InetAddress.getByName(ip).getAddress();
        assertEquals(parseDottedQuadToInt(ip), bytesToInt(bytes));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.0.0",
            "255.255.255.255",
            "192.168.1.10",
            "203.0.113.7",
            "10.0.0.1"
    })
    void toInet4Address_roundtrip(String ip) {
        int asInt = parseDottedQuadToInt(ip);
        Inet4Address inet = toInet4Address(asInt);
        assertEquals(ip, inet.getHostAddress());

        assertEquals(asInt, bytesToInt(inet.getAddress()));
    }

    @Test
    void toInet4Address_handles_high_bit_addresses() {
        String ip = "200.1.1.1";
        assertEquals(ip, toInet4Address(parseDottedQuadToInt(ip)).getHostAddress());
    }

    @Test
    void bytesToInt_requires_4_bytes() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> bytesToInt(new byte[]{1, 2, 3}));
    }
}