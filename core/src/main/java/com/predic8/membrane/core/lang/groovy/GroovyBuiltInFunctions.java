package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.lang.*;
import groovy.lang.*;

/**
 * Helper class for built-in functions that delegates to the implementation CommonBuiltInFunctions.
 *
 * TODO Wrap the other functions here too, so that we have the same as for SpEL. Difference: The function are called with ${fn.functionname()} instead of ${functionname()} in the template interceptor
 *
 */
public class GroovyBuiltInFunctions extends GroovyObjectSupport {

    private Exchange exchange;

    public GroovyBuiltInFunctions(Exchange exchange) {
        this.exchange = exchange;
    }

    public String user() {
        return CommonBuiltInFunctions.user(exchange);
    }
}
