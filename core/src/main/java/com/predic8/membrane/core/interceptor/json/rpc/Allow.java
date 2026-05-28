package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCElement;

/**
 * @description Permits JSON-RPC requests whose <code>method</code> matches the configured regular expression.
 */
@MCElement(name = "allow", collapsed = true, component = false, id = "rpc-allow")
public class Allow extends Rule {

    @Override
    boolean permits() {
        return true;
    }

    @Override
    public String toString() {
        return "Allow{method=%s}".formatted(getMethod());
    }
}
