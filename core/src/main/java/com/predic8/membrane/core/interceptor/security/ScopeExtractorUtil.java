package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.core.exchange.*;

import java.util.*;

/**
 * Extracts a list of scopes from the Exchange. Because different plugins
 * put scopes at different locations in the Exchange this class encapsulates
 * accessing scopes
 *
 */
public class ScopeExtractorUtil {

    public static Set<String> getScopes(Exchange exc) {

        Set<String> scopes = new HashSet<>();

        if (exc.getProperty("jwt") instanceof Map jwt) {
            if(jwt.get("scp") instanceof String scp) {

            }
        }

        // API Key Scopes

        return scopes;
    }
}
