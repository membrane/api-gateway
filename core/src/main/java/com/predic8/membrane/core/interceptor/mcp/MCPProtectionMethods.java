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
