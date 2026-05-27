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

package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.predic8.membrane.core.mcp.MCPInitialize.METHOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MCPInitializeTest {

    @Test
    void parsesRequiredInitializeFields() {
        MCPInitialize initialize = new MCPInitialize(new JSONRPCRequest(
                1,
                METHOD,
                Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of("roots", Map.of()),
                        "clientInfo", Map.of("name", "test-client", "version", "1.0.0")
                )
        ));

        assertEquals("2025-03-26", initialize.getProtocolVersion());
        assertEquals("test-client", initialize.getClientInfo().getName());
        assertEquals("1.0.0", initialize.getClientInfo().getVersion());
    }

    @Test
    void rejectsMissingCapabilities() {
        JSONRPCRequest request = new JSONRPCRequest(
                1,
                METHOD,
                Map.of(
                        "protocolVersion", "2025-03-26",
                        "clientInfo", Map.of("name", "test-client", "version", "1.0.0")
                )
        );

        assertThrows(IllegalArgumentException.class, () -> new MCPInitialize(request));
    }

    @Test
    void rejectsMissingClientInfo() {
        JSONRPCRequest request = new JSONRPCRequest(
                1,
                METHOD,
                Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of()
                )
        );

        assertThrows(IllegalArgumentException.class, () -> new MCPInitialize(request));
    }

    @Test
    void rejectsBlankClientVersion() {
        JSONRPCRequest request = new JSONRPCRequest(
                1,
                METHOD,
                Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "test-client", "version", " ")
                )
        );

        assertThrows(IllegalArgumentException.class, () -> new MCPInitialize(request));
    }
}
