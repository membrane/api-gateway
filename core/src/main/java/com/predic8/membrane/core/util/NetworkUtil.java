/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import java.io.*;
import java.net.*;

import static java.lang.Integer.parseInt;
import static java.net.InetAddress.getByAddress;

public class NetworkUtil {

    public static int getFreePortEqualAbove(int port) throws IOException {
        for (int p = port; p <= 65535; p++) {
            try (ServerSocket ignored = new ServerSocket(p)) {
                return p;
            } catch (Exception ignored) {
            }
        }
        throw new IOException("Could not find a free port");
    }

    public static Pair<byte[], Integer> readUpTo1KbOfDataFrom(Socket sourceSocket, byte[] buffer) throws IOException {
        int available = sourceSocket.getInputStream().available();
        int offset = 0;
        while (available > 0) {
            if (available > buffer.length - offset) {
                available = buffer.length - offset;

                //noinspection ResultOfMethodCallIgnored
                sourceSocket.getInputStream().read(buffer, offset, available);
                offset += available;
                break;
            } else {
                sourceSocket.getInputStream().read(buffer, offset, available);
                offset += available;
                available = sourceSocket.getInputStream().available();
            }
        }
        return new Pair<>(buffer, offset);
    }

    /**
     * Removes surrounding square brackets from an address literal, if present.
     * Useful for inputs like "[2001:db8::1]" where the brackets are part of the textual form.
     */
    public static String removeBracketsIfPresent(String s) {
        if (s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }


    /**
     * Parses a validated dotted-quad IPv4 literal into a 32-bit int (big endian).
     */
    public static int parseDottedQuadToInt(String s) {
        String[] p = s.split("\\.", 4);
        return (parseInt(p[0]) << 24)
                | (parseInt(p[1]) << 16)
                | (parseInt(p[2]) << 8)
                | parseInt(p[3]);
    }

    /**
     * Converts a 32-bit IPv4 int (big endian) to Inet4Address. (>>> avoids sign extension)
     */
    public static Inet4Address toInet4Address(int ip) {
        byte[] bytes = new byte[]{
                (byte) (ip >>> 24),
                (byte) (ip >>> 16),
                (byte) (ip >>> 8),
                (byte) ip
        };
        try {
            return (Inet4Address) getByAddress(bytes);
        } catch (UnknownHostException e) {
            // length=4 => should never happen
            throw new IllegalStateException(e);
        }
    }

    /**
     * Compares two addresses by the first {@code prefixBits} bits.
     * Works for IPv4 (4 bytes) and IPv6 (16 bytes) as long as both arrays have the same length.
     */
    public static boolean matchesPrefix(byte[] a, byte[] b, int prefixBits) {
        if (a.length != b.length)
            throw new IllegalArgumentException("Address length mismatch: " + a.length + " vs " + b.length);

        if (prefixBits <= 0) return true;
        if (prefixBits >= a.length * 8) {
            for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
            return true;
        }
        int remainingBits = prefixBits % 8;

        for (int i = 0; i < prefixBits / 8; i++) {
            if (a[i] != b[i]) return false;
        }

        if (remainingBits == 0) return true;

        int partialMask = 0xFF << (8 - remainingBits);
        return (a[prefixBits / 8] & partialMask) == (b[prefixBits / 8] & partialMask);
    }
}
