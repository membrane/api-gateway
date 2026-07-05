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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class JWTSecurityScheme extends AbstractSecurityScheme {

    /**
     * @param jwt claims of the validated JSON Web Token; the value of the {@code scopesClaim}
     *            entry may be a space separated string or a list
     * @param scopesClaim name of the claim holding the scopes, e.g. "scp" (Microsoft Entra ID)
     *                    or "scope" (RFC 9068)
     */
    public JWTSecurityScheme(Map<String, Object> jwt, String scopesClaim) {
        var scopes = jwt.get(scopesClaim);
        if (scopes instanceof String scopeString) {
            this.scopes = new HashSet<>(Arrays.asList(scopeString.split(" +")));
        }
        if (scopes instanceof List<?> scopeList) {
            this.scopes = new HashSet<>(scopeList.stream().map(Object::toString).toList());
        }
    }


    @Override
    public String getName() {
        return "jwt";
    }
}
