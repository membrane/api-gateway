package com.predic8.membrane.core.security;

import java.util.*;

public class JWTSecurityScheme extends AbstractSecurityScheme {

    /**
     * TODO
     * @param jwt JSON Web Token
     */
    public JWTSecurityScheme(Map<String, Object> jwt) {
        var scopes = jwt.get("scp");
        if (scopes != null) {
            if (scopes instanceof String scopeString) {
                this.scopes = new HashSet<>(Arrays.asList(scopeString.split(" +")));
            }
        }
    }
}
