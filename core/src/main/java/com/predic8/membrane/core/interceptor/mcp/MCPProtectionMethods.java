package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "methods", id = "mcp-methods")
public class MCPProtectionMethods {

    private boolean toolsList = true;
    private boolean toolsCall = true;
    private boolean notifications = true;

    @MCAttribute
    public void setToolsList(boolean toolsList) {
        this.toolsList = toolsList;
    }

    public boolean isToolsList() {
        return toolsList;
    }

    @MCAttribute
    public void setToolsCall(boolean toolsCall) {
        this.toolsCall = toolsCall;
    }

    public boolean isToolsCall() {
        return toolsCall;
    }

    @MCAttribute
    public void setNotifications(boolean notifications) {
        this.notifications = notifications;
    }

    public boolean isNotifications() {
        return notifications;
    }
}
