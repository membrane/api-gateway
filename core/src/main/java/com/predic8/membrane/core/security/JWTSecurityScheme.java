package com.predic8.membrane.core.security;

import java.util.*;

public class JWTSecurityScheme extends AbstractSecurityScheme {

    /**
     * TODO
     * @param jwt
     */
    public JWTSecurityScheme(Map<String, Object> jwt) {
        var scopes = jwt.get("scp");
        System.out.println("scopes = " + scopes);
        System.out.println("scopes = " + scopes.getClass());
    }
}
