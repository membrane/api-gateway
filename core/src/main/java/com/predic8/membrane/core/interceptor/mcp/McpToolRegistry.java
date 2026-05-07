package com.predic8.membrane.core.interceptor.mcp;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class McpToolRegistry {

    private final Map<String, McpToolDefinition> tools = new LinkedHashMap<>();

    public McpToolRegistry register(McpToolDefinition definition) {
        if (tools.putIfAbsent(definition.name(), definition) != null) {
            throw new IllegalArgumentException("Duplicate MCP tool registration: " + definition.name());
        }
        return this;
    }

    public McpToolDefinition find(String name) {
        return tools.get(name);
    }

    public Collection<McpToolDefinition> list() {
        return tools.values();
    }
}
