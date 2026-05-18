package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class ContentBlockStart {

    private ToolUse toolUse;

    public static ContentBlockStart from(ObjectNode on) {
        var cbs = new ContentBlockStart();
        var cb = (ObjectNode) on.path("content_block");

        if ("tool_use".equals(cb.path("type").asText())) {
            cbs.toolUse = ToolUse.from(cb);
        }

        return cbs;
    }

    public ToolUse getToolUse() {
        return toolUse;
    }
}
