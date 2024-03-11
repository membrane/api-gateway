package com.predic8.membrane.core.security;

public class BasicHttpSecurityScheme extends HttpSecurityScheme {

    @Override
    public int hashCode() {
        // There is no field yet, so there are no two different BasicHttpSecurityScheme
        return 123;
    }

    @Override
    public boolean equals(Object obj) {
        // There is no field yet, so there are no two different BasicHttpSecurityScheme
        return obj instanceof BasicHttpSecurityScheme;
    }

    @Override
    public String toString() {
        return "basic scheme";
    }
}
