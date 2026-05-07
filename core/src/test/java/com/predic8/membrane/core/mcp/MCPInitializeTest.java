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
