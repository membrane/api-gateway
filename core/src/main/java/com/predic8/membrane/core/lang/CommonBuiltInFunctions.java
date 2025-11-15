package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.security.*;

import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;

/**
 * Place to share built-in functions between SpEL and Groovy.
 *
 * TODO Move function implementations from com.predic8.membrane.core.lang.spel.functions.BuiltInFunctions to here.
 *
 */
public class CommonBuiltInFunctions {

    public static String user(Exchange exchange) {
        List<SecurityScheme> schemes = exchange.getProperty(SECURITY_SCHEMES, List.class );
        for (SecurityScheme scheme :schemes) {
            if (scheme instanceof BasicHttpSecurityScheme basic) {
                return basic.getUsername();
            }
        }
        return null;
    }
}
