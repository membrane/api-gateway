package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@MCElement(name = "methods", component = false, id = "json-rpc-method-definitions")
public class JsonRPCMethodDefinitions {

    private final Map<String, JsonRPCSchemas> methods = new LinkedHashMap<>();

    @MCOtherAttributes
    public void setMethods(Map<String, JsonRPCSchemas> methods) {
        if (methods != null) {
            this.methods.putAll(methods);
        }
    }

    public Map<String, JsonRPCSchemas> getMethods() {
        return methods;
    }
}
