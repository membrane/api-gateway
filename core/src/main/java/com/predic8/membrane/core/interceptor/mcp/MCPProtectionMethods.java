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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

/**
 * @description
 * <p>Controls the optional MCP methods accepted by
 * <code>mcpProtection</code>.</p>
 *
 * <p>
 * <code>initialize</code> and <code>ping</code> are always allowed.
 * Unsupported MCP methods are always rejected.</p>
 */
@MCElement(name = "methods", id = "mcp-methods")
public class MCPProtectionMethods {

    private boolean toolsList = true;
    private boolean toolsCall = true;
    private boolean notifications = true;

    /**
     * @description Enables the MCP <code>tools/list</code> method.
     * @default true
     */
    @MCAttribute
    public void setToolsList(boolean toolsList) {
        this.toolsList = toolsList;
    }

    public boolean isToolsList() {
        return toolsList;
    }

    /**
     * @description Enables the MCP <code>tools/call</code> method.
     * @default true
     */
    @MCAttribute
    public void setToolsCall(boolean toolsCall) {
        this.toolsCall = toolsCall;
    }

    public boolean isToolsCall() {
        return toolsCall;
    }

    /**
     * @description Enables MCP methods whose names start with
     * <code>notifications/</code>.
     * @default true
     */
    @MCAttribute
    public void setNotifications(boolean notifications) {
        this.notifications = notifications;
    }

    public boolean isNotifications() {
        return notifications;
    }
}
