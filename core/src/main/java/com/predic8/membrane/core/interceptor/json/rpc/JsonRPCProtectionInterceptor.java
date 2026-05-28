package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;

import java.util.List;
import java.util.Map;

@MCElement(name = "jsonRPCProtection")
public class JsonRPCProtectionInterceptor extends AbstractInterceptor {

    BatchRule batchRule;

    @MCChildElement(order = 0)
    public void setBatch(BatchRule batchRule) {
        this.batchRule = batchRule;
    }

    @MCChildElement(order = 1)
    public void setMethods(List<Rule> methods) {
        // TODO
    }

    @MCOtherAttributes
    public void setParams(Map<String, String> params) {
        // TODO
    }

}
