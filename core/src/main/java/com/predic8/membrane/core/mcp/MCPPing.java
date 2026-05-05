package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

public class MCPPing extends MCPRequest {

    public static final String METHOD = "ping";

    public MCPPing(JSONRPCRequest request) {
        super(request, METHOD);
    }

    public static MCPPing from(JSONRPCRequest request) {
        return new MCPPing(request);
    }
}
