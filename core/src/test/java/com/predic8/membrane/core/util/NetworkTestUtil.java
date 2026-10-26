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
package com.predic8.membrane.core.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class NetworkTestUtil {

    private NetworkTestUtil() {
    }

    /**
     * Returns a port the OS reported as free a moment ago. Nothing listens on it when this returns.
     * Another process can take it before the caller binds, so a test that opens the listening socket
     * itself should bind {@code new ServerSocket(0)} directly and read {@code getLocalPort()} instead.
     */
    public static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
    /**
     * Returns distinct candidate ports, keeping all probe sockets open until allocation is complete.
     * As with {@link #freePort()}, callers must bind promptly: these ports are not reserved on return.
     */
    public static int[] freePorts(int count) throws IOException {
        List<ServerSocket> sockets = new ArrayList<>();
        try {
            int[] ports = new int[count];
            for (int i = 0; i < count; i++) {
                ServerSocket socket = new ServerSocket(0);
                sockets.add(socket);
                ports[i] = socket.getLocalPort();
            }
            return ports;
        } finally {
            for (ServerSocket socket : sockets)
                socket.close();
        }
    }
}
