package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.mcp.MCPInitialize.ClientInfo;

import static com.predic8.membrane.core.interceptor.mcp.McpSessionContext.McpSessionState.*;

public final class McpSessionContext {

    private McpSessionState state = NEW;

    // TODO: only if we want to support multiple versions (maybe in the future?)
    private String negotiatedProtocolVersion;
    private ClientInfo clientInfo;

    public synchronized McpSessionState getState() {
        return state;
    }

    public synchronized boolean initialize(String protocolVersion, ClientInfo clientInfo) {
        if (state != NEW) {
            return false;
        }
        negotiatedProtocolVersion = protocolVersion;
        this.clientInfo = clientInfo;
        state = INITIALIZED;
        return true;
    }

    public synchronized boolean markReady() {
        if (state != INITIALIZED) {
            return false;
        }
        state = READY;
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public synchronized boolean isIn(McpSessionState... states) {
        for (McpSessionState candidate : states) {
            if (state == candidate) {
                return true;
            }
        }
        return false;
    }

    public synchronized String getNegotiatedProtocolVersion() {
        return negotiatedProtocolVersion;
    }

    public synchronized ClientInfo getClientInfo() {
        return clientInfo;
    }

    public enum McpSessionState {
        NEW,
        INITIALIZED,
        READY,
        CLOSED
    }

}
