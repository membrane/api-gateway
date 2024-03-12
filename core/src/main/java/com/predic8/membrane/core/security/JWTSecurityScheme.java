package com.predic8.membrane.core.security;

import com.predic8.membrane.core.interceptor.jwt.*;
import io.swagger.v3.oas.models.security.*;

import java.util.*;

public class JWTSecurityScheme extends AbstractSecurityScheme {

    public JWTSecurityScheme(Map<String, Object> jwt) {
        Object scopes = jwt.get("scopes");
    }
}
