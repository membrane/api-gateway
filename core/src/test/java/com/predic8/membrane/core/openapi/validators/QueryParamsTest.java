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

package com.predic8.membrane.core.openapi.validators;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


public class QueryParamsTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/query-params.yml";
    }

    @Test
    void differentTypesOk()  {
        assertEquals(0, validator.validate(get().path("/cities?limit=10")).size());
    }

    @Test
    void invalidQueryParam()  {
        ValidationErrors errors = validator.validate(get().path("/cities?limit=200"));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("limit",e.getContext().getValidatedEntity());
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST/QUERY_PARAMETER/limit", e.getContext().getLocationForRequest());
    }

    @Test
    void unknownQueryParam() {
        ValidationErrors errors = validator.validate(get().path("/cities?unknown=3&limit=10"));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("REQUEST/QUERY_PARAMETER", e.getContext().getLocationForRequest());
        assertTrue(e.getMessage().contains("parameter 'unknown' is invalid"));
    }

    @Test
    void missingRequiredParam() {
        ValidationErrors err = validator.validate(get().path("/cities"));
        assertEquals(1,err.size());

        ValidationError e = err.get(0);
        assertEquals(400, e.getContext().getStatusCode());
        assertTrue(e.getMessage().contains("'limit' missing."));
    }


    @Test
    void queryParamAtPathLevel()  {
        ValidationErrors errors = validator.validate(get().path("/cities?foo=-1&limit=10"));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("foo",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/QUERY_PARAMETER/foo", e.getContext().getLocationForRequest());
    }

    @Test
    void escapedTest() {
        ValidationErrors errors = validator.validate(get().path("/cities?name=Bad%20Godesberg&limit=10"));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("REQUEST/QUERY_PARAMETER/name", e.getContext().getLocationForRequest());
    }

    @Test
    void utf8Test() {
        assertEquals(0, validator.validate(get().path("/cities?name=K%C3%B6%C3%B6%C3%B6ln&limit=10")).size());
    }

    @Test
    void referencedParamTest() {
        assertEquals(0, validator.validate(get().path("/cities?limit=1&page=10")).size());
    }

    @Test
    public void referencedParamValueTest()  {
        ValidationErrors errors = validator.validate(get().path("/cities?limit=1&page=-1"));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("page",e.getContext().getValidatedEntity());
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST/QUERY_PARAMETER/page", e.getContext().getLocationForRequest());
        assertEquals(400,e.getContext().getStatusCode());
    }
}