package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.SCOPES;
import static java.util.List.of;

/**
 * Extracts a list of scopes from the Exchange. Because different plugins
 * put scopes at different locations in the Exchange this class encapsulates
 * accessing scopes.
 */
public class ScopeExtractorUtil {

    public static Set<String> getScopes(Exchange exc) {

        Set<String> combinedScopes = new HashSet<>();

        if (exc.getProperty("jwt") instanceof Map jwt) {
            if(jwt.get("scp") instanceof String scp) {
                combinedScopes.addAll(of(scp.split(" ")));
            }
        }


        if (exc.getProperty(SCOPES) instanceof List scopes) {
            for (Object scope : scopes) {
                if (scope instanceof String s) combinedScopes.add(s);
            }
        }

        return combinedScopes;
    }
}
