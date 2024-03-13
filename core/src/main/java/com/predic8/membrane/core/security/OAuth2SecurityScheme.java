package com.predic8.membrane.core.security;

public class OAuth2SecurityScheme extends AbstractSecurityScheme {

    public final Flow flow;

    public static OAuth2SecurityScheme IMPLICIT() {
        return new OAuth2SecurityScheme(Flow.IMPLICIT);
    }

    public static OAuth2SecurityScheme PASSWORD() {
        return new OAuth2SecurityScheme(Flow.PASSWORD);
    }

    public static OAuth2SecurityScheme CLIENT_CREDENTIALS() {
        return new OAuth2SecurityScheme(Flow.CLIENT_CREDENTIALS);
    }

    public static OAuth2SecurityScheme AUTHORIZATION_CODE() {
        return new OAuth2SecurityScheme(Flow.AUTHORIZATION_CODE);
    }

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
}
