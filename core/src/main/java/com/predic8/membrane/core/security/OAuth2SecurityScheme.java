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
