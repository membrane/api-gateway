package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCElement;

@MCElement(name = "deny", collapsed = true, component = false, id = "rpc-deny")
public class Deny extends Rule {

    @Override
    boolean permits() {
        return false;
    }

    @Override
    public String toString() {
        return "Deny{method=%s}".formatted(getMethod());
    }
}
