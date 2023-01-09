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

import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


public class ReferencesTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/references.yml";
    }

    @Test
    public void pathParamOk()  {
        ValidationErrors errors = validator.validate(Request.get().path("/references/6"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void pathParamInvalid()  {
        ValidationErrors errors = validator.validate(Request.get().path("/references/foo"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(PATH_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("rid",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/PATH_PARAMETER/rid", e.getContext().getLocationForRequest());

    }

    @Test
    public void queryParamOk() {
        ValidationErrors errors = validator.validate(Request.get().path("/references/6?limit=10"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void queryParamInvalid() {
        ValidationErrors errors = validator.validate(Request.get().path("/references/6?limit=150"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("limit",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/QUERY_PARAMETER/limit", e.getContext().getLocationForRequest());
    }

    @Test
    public void bodyAsRefPrimitiveOk() {
        ValidationErrors errors = validator.validate(Request.post().path("/body-as-ref-primitive").json().body("42"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void bodyAsRefPrimitiveInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/body-as-ref-primitive").json().body("-1"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/BODY", e.getContext().getLocationForRequest());
    }

    @Test
    public void objRefsObjOK() {
        ValidationErrors errors = validator.validate(Request.post().path("/obj-ref-obj").json().body(getResourceAsStream("/openapi/messages/references-customer-ok.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void objRefsObjOKInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/obj-ref-obj").json().body(getResourceAsStream("/openapi/messages/references-customer-invalid.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationContext ctx = errors.get(0).getContext();
        assertEquals("REQUEST",ctx.getValidatedEntity());
        assertEquals("string",ctx.getSchemaType());
        assertEquals(400,ctx.getStatusCode());
        assertEquals("/contract/details", ctx.getJSONpointer());
        assertEquals("REQUEST/BODY#/contract/details", ctx.getLocationForRequest());
    }
}