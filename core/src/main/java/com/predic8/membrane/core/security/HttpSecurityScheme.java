package com.predic8.membrane.core.security;

public abstract class HttpSecurityScheme extends AbstractSecurityScheme {

    public static BasicHttpSecurityScheme BASIC() {
        return new BasicHttpSecurityScheme();
    }

    public static BearerHttpSecurityScheme BEARER() {
        return new BearerHttpSecurityScheme();
    }

}
