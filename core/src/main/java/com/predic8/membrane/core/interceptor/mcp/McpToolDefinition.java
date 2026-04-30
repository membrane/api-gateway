package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.mcp.MCPToolsListResponse;

import java.util.Map;

public record McpToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema,
        McpToolHandler handler
) {
    public MCPToolsListResponse.Tool toTool() {
        return new MCPToolsListResponse.Tool(name, description, inputSchema);
    }
}
