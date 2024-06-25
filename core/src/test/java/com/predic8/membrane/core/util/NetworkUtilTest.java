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

import java.io.IOException;
import java.net.ServerSocket;

import static com.predic8.membrane.core.util.NetworkUtil.getFreePortEqualAbove;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}