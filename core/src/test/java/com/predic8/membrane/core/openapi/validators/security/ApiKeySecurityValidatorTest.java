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

import static com.predic8.membrane.core.http.Request.In.*;
import static org.junit.jupiter.api.Assertions.*;


public class ApiKeySecurityValidatorTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/security/api-key.yml";
    }

    @Test
    void noHeader() {
        var errors = validator.validate(Request.get().path("/in-header"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("by API key"));
    }

    @Test
    void inHeaderIgnoreCase() {
        assertTrue(validator.validate(Request.get().path("/in-header").securitySchemes(List.of(new ApiKeySecurityScheme(HEADER, "X-Api-KEY")))).isEmpty());
    }

    @Test
    void inQuery() {
        assertTrue(validator.validate(Request.get().path("/in-query").securitySchemes(List.of(new ApiKeySecurityScheme(QUERY, "api-key")))).isEmpty());
    }

    @Test
    void inCookieIgnoreCase() {
        assertTrue(validator.validate(Request.get().path("/in-cookie").securitySchemes(List.of(new ApiKeySecurityScheme(COOKIE, "api-key")))).isEmpty());
    }

    @Test
    void inHeaderWrongName() {
        ValidationErrors errors = validator.validate(Request.get().path("/in-header").securitySchemes(List.of(new ApiKeySecurityScheme(HEADER, "APIKEY"))));
        System.out.println("errors = " + errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("api-key is APIKEY but should be X-API-KEY"));
    }

    @Test
    void inHeaderWrongIn() {
        ValidationErrors errors = validator.validate(Request.get().path("/in-header").securitySchemes(List.of(new ApiKeySecurityScheme(QUERY, "X-API-KEY"))));
        System.out.println("errors = " + errors);
        assertEquals(1, errors.size());
        assertEquals(403, errors.get(0).getContext().getStatusCode());
        assertTrue(errors.get(0).getMessage().contains("Api-key is in QUERY but should be in header"));
    }
}