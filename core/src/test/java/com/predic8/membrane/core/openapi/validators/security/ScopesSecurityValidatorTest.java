/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators.security;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.SECURITY;
import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.X_MEMBRANE_VALIDATION;
import static com.predic8.membrane.core.security.OAuth2SecurityScheme.CLIENT_CREDENTIALS;
import static org.junit.jupiter.api.Assertions.*;


public class ScopesSecurityValidatorTest extends AbstractValidatorTest {


    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/security/security.yml";
    }

    @BeforeEach
    void setup() {
        var map = new HashMap<String,Boolean>();
        map.put(SECURITY,true);
        validator.getApi().addExtension(X_MEMBRANE_VALIDATION,map);
    }

    @Test
    void simpleHasScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance").securitySchemes(List.of(CLIENT_CREDENTIALS().scopes("finance","read"))));
        assertEquals(0,errors.size());
    }

    @Test
    void simpleMissingAllScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write").securitySchemes(List.of(CLIENT_CREDENTIALS())));
        assertEquals(3,errors.size());
    }

    @Test
    void simpleMissingTwoScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write").securitySchemes(List.of(CLIENT_CREDENTIALS().scopes("read"))));
        assertEquals(2,errors.size());
    }

    @Test
    void simpleIgnoreAdditionalScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance")
                .securitySchemes(List.of(CLIENT_CREDENTIALS().scopes("read", "finance", "development"))));
        assertEquals(0,errors.size());
    }
}