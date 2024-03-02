package com.predic8.membrane.core.security;

import java.util.*;

public class OAuth2SecurityScheme implements SecurityScheme, Scopes {

    public final Flow flow;

    public static OAuth2SecurityScheme IMPLICIT = new OAuth2SecurityScheme(Flow.IMPLICIT);
    public static OAuth2SecurityScheme PASSWORD = new OAuth2SecurityScheme(Flow.PASSWORD);

    public static OAuth2SecurityScheme CLIENT_CREDENTIALS = new OAuth2SecurityScheme(Flow.CLIENT_CREDENTIALS);

    public static OAuth2SecurityScheme AUTHORIZATION_CODE = new OAuth2SecurityScheme(Flow.AUTHORIZATION_CODE);

    public Set<String> scopes;

    public OAuth2SecurityScheme(Flow flow) {
        this.flow = flow;
    }

    public enum Flow {
        IMPLICIT("implicit"), PASSWORD("password"), CLIENT_CREDENTIALS("clientCredentials"), AUTHORIZATION_CODE("authorizationCode");

        public final String value;

        Flow(String flow) {
            this.value = flow;
        }
    }

    public OAuth2SecurityScheme scopes(String... scopes) {
        this.scopes = new HashSet<>(Arrays.stream(scopes).toList());
        return this;
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    @Override
    public Set<String> getScopes() {
        return scopes;
    }
}
