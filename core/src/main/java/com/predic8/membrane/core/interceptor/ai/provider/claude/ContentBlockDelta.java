package com.predic8.membrane.core.interceptor.ai.provider.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ContentBlockDelta {

    private int index;
    private String deltaType;
    private String partialJson;

    public static ContentBlockDelta from(ObjectNode on) {
        var cbd = new ContentBlockDelta();

        cbd.index = on.path("index").asInt();

        JsonNode delta = on.path("delta");
        cbd.deltaType = delta.path("type").asText(null);
        cbd.partialJson = delta.path("partial_json").asText("");

        return cbd;
    }

    public boolean isInputJsonDelta() {
        return "input_json_delta".equals(deltaType);
    }

    public int getIndex() {
        return index;
    }

    public String getDeltaType() {
        return deltaType;
    }

    public String getPartialJson() {
        return partialJson;
    }
}
