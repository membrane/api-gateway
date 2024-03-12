package com.predic8.membrane.core.security;

import com.predic8.membrane.core.exchange.*;

import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;

public abstract class AbstractSecurityScheme implements SecurityScheme {

    protected Set<String> scopes = new HashSet<>();

    @Override
    public void add(Exchange exc) {

        try {
            @SuppressWarnings("unchecked")
            var securitySchemes = (List<SecurityScheme>) exc.getProperty(SECURITY_SCHEMES);

            if (securitySchemes == null) {
                securitySchemes = new ArrayList<>();
                exc.setProperty(SECURITY_SCHEMES,securitySchemes);
            }

            securitySchemes.add(this);
        } catch (Exception e) {
            // TODO Log
        }
    }

    public AbstractSecurityScheme scopes(String... scopes) {
        this.scopes = new HashSet<>(Arrays.stream(scopes).toList());
        return this;
    }

    public AbstractSecurityScheme scopes(Set<String> scopes) {
        this.scopes = scopes;
        return this;
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    public Set<String> getScopes() {
        return scopes;
    }
}
