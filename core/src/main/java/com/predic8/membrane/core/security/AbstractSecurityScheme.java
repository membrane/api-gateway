/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
