package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "schemaValidation", component = false)
public class JsonRPCSchemaValidation {

    private JsonRPCErrorValidation errorValidation;
    private JsonRPCMethodDefinitions methods = new JsonRPCMethodDefinitions();

    public JsonRPCErrorValidation getErrorValidation() {
        return errorValidation;
    }

    @MCChildElement(order = 1)
    public void setErrorValidation(JsonRPCErrorValidation errorValidation) {
        this.errorValidation = errorValidation;
    }

    @MCChildElement(order = 2)
    public void setMethods(JsonRPCMethodDefinitions methods) {
        this.methods = methods;
    }

    public JsonRPCMethodDefinitions getMethods() {
        return methods;
    }
}
