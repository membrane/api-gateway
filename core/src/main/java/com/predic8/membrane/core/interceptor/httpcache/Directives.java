package com.predic8.membrane.core.interceptor.httpcache;

public enum Directives {
    NO_CACHE("no-cache"),
    NO_STORE("no-store"),
    MAX_AGE("max-age"),
    NO_TRANSFORM("no-transform"),
    ONLY_IF_CACHED("only-if-cached"),
    MAX_STALE("max-stale"),
    MIN_FRESH("min-fresh"),
    S_MAXAGE("s-maxage"),
    MUST_REVALIDATE("must-revalidate"),
    PROXY_REVALIDATE("proxy-revalidate"),
    PUBLIC("public"),
    PRIVATE("private"),
    MUST_UNDERSTAND("must-understand"),
    IMMUTABLE("immutable"),
    STALE_WHILE_REVALIDATE("stale-while-revalidate"),
    STALE_IF_ERROR("stale-if-error");

    private final String directiveName;

    Directives(String directiveName) {
        this.directiveName = directiveName;
    }

    public String asString() {
        return directiveName;
    }

    public static Directives fromString(String text) {
        for (Directives directive : Directives.values()) {
            if (directive.asString().equalsIgnoreCase(text)) {
                return directive;
            }
        }
        return null;
    }
}
