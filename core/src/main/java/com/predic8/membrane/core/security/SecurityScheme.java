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

public interface SecurityScheme {

    /**
     * Name of the scheme
     * Use of OpenAPI names is encouraged: "apiKey", "http", "mutualTLS", "oauth2", "openIdConnect"
     * See: https://swagger.io/specification/#security-scheme-object
     * @return
     */
    String getName();

    void add(Exchange exchange);

    boolean hasScope(String scope);

    Set<String> getScopes();
}
