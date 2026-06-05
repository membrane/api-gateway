package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@MCElement(name = "schema", component = false, id = "json-rpc-inline-schema")
public class JsonRPCInlineSchema {

    private final Map<String, Object> properties = new LinkedHashMap<>();

    @MCOtherAttributes
    public void setProperties(Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
