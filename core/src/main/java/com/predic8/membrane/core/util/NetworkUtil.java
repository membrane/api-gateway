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
     * Builds an IPv4 subnet mask from a CIDR prefix (/0..32).
     * /n => top n bits are 1, remaining bits are 0.
     */
    public static int maskOf(int prefix) {
        if (prefix <= 0) return 0;
        if (prefix >= 32) return 0xFFFFFFFF;
        return (int) (0xFFFFFFFFL << (32 - prefix));
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
     * Converts 4 IPv4 bytes (network order) to a 32-bit int. (& 0xFF makes bytes unsigned)
     */
    public static int bytesToInt(byte[] b) {
        return ((b[0] & 0xFF) << 24)
                | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8)
                | (b[3] & 0xFF);
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
}
