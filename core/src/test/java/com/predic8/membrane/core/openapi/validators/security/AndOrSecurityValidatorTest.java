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
import com.predic8.membrane.core.security.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static com.predic8.membrane.core.security.BasicHttpSecurityScheme.*;
import static org.junit.jupiter.api.Assertions.*;


public class AndOrSecurityValidatorTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/security/and-or.yml";
    }


    @Test
    void orBasic() {
        assertEquals(0, validator.validate(Request.get().path("/one").securitySchemes(List.of(BASIC()))).size());
    }

    @Test
    void orApiKey() {
        assertEquals(0, validator.validate(Request.get().path("/one").securitySchemes(List.of(new ApiKeySecurityScheme(HEADER,"X-API-KEY")))).size());
    }

    @Test
    void orBothKey() {
        var errors = validator.validate(Request.get().path("/one").securitySchemes(List.of(new ApiKeySecurityScheme(HEADER,"X-API-KEY"), BASIC())));
        assertEquals(0, errors.size());
    }


    @Test
    void bothWithOneBasic() {
        var errors = validator.validate(Request.get().path("/both").securitySchemes(List.of(BASIC())));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("Authentication by API key is required"));
    }

    @Test
    void bothWithOneApiKey() {
        var errors = validator.validate(Request.get().path("/both").securitySchemes(List.of(new ApiKeySecurityScheme(HEADER,"x-api-key"))));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("Caller ist not authenticated with HTTP and basic scheme"));
    }
}