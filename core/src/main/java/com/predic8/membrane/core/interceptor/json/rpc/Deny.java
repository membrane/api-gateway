package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCElement;

/**
 * @description Denies JSON-RPC requests whose <code>method</code> matches the configured regular expression.
 */
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
