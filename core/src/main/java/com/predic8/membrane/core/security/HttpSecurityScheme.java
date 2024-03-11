package com.predic8.membrane.core.security;

public abstract class HttpSecurityScheme implements SecurityScheme {

    public static BasicHttpSecurityScheme BASIC() {
        return new BasicHttpSecurityScheme();
    }

    public static BearerHttpSecurityScheme BEARER() {
        return new BearerHttpSecurityScheme();
    }

}
