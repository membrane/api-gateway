package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolUse {

    private static final Logger log = LoggerFactory.getLogger(ToolUse.class);

    private String name;

    public static ToolUse from(ObjectNode on) {
        var tu = new ToolUse();
        tu.name = on.path("name").asText();
        return tu;
    }

    public String getName() {
        return name;
    }
}
