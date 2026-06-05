package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "method", component = false, id = "json-rpc-method-schema")
public class JsonRPCSchemas {

    private JsonRPCParamValidation paramValidation;

    private JsonRPCResponseValidation responseValidation;

    @MCChildElement(order = 1)
    public void setParams(JsonRPCParamValidation paramValidation) {
        this.paramValidation = paramValidation;
    }

    public JsonRPCParamValidation getParams() {
        return paramValidation;
    }

    @MCChildElement(order = 2)
    public void setResponse(JsonRPCResponseValidation responseValidation) {
        this.responseValidation = responseValidation;
    }

    public JsonRPCResponseValidation getResponse() {
        return responseValidation;
    }
}
