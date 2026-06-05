package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;

public class SchemaSetter {

    protected String location;
    protected JsonRPCInlineSchema schema;

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    @MCChildElement(order = 1)
    public void setSchema(JsonRPCInlineSchema schema) {
        this.schema = schema;
    }

    public JsonRPCInlineSchema getSchema() {
        return schema;
    }
}
