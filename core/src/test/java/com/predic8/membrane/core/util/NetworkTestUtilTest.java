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

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static org.junit.jupiter.api.Assertions.*;

class NetworkTestUtilTest {

    @Test
    void freePortCanBeBound() throws Exception {
        final var port = freePort();
        assertTrue(port > 0 && port <= 65535);
        try (var socket = new ServerSocket(port)) {
            assertEquals(port, socket.getLocalPort());
        }
    }
}
