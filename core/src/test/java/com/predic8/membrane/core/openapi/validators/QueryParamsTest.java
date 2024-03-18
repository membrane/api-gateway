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

import com.predic8.membrane.core.openapi.model.Request;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class QueryParamsTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/query-params.yml";
    }

    @Test
    public void differentTypesOk()  {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?limit=10"));
        assertEquals(0,errors.size());
    }

    @Test
    public void invalidQueryParam()  {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?limit=200"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("limit",e.getContext().getValidatedEntity());
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST/QUERY_PARAMETER/limit", e.getContext().getLocationForRequest());
    }

    @Test
    public void unknownQueryParam() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?unknown=3&limit=10"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("REQUEST/QUERY_PARAMETER", e.getContext().getLocationForRequest());
    }

    @Test
    public void missingRequiredParam() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
    }


    @Test
    public void queryParamAtPathLevel()  {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?foo=-1&limit=10"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("foo",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/QUERY_PARAMETER/foo", e.getContext().getLocationForRequest());
    }

    @Test
    public void escapedTest() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?name=Bad%20Godesberg&limit=10"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("REQUEST/QUERY_PARAMETER/name", e.getContext().getLocationForRequest());
    }

    @Test
    public void utf8Test() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?name=K%C3%B6%C3%B6%C3%B6ln&limit=10"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void referencedParamTest() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?limit=1&page=10"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void referencedParamValueTest()  {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?limit=1&page=-1"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("page",e.getContext().getValidatedEntity());
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST/QUERY_PARAMETER/page", e.getContext().getLocationForRequest());
        assertEquals(400,e.getContext().getStatusCode());
    }



}