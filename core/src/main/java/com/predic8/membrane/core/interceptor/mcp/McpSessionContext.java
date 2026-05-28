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
