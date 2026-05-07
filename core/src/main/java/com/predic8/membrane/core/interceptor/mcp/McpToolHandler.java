package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.mcp.MCPToolsCall;
import com.predic8.membrane.core.mcp.MCPToolsCallResponse;

@FunctionalInterface
public interface McpToolHandler {

    MCPToolsCallResponse handle(MCPToolsCall call, Exchange exc) throws Exception;

}
