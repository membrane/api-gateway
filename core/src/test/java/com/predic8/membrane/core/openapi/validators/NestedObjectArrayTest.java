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

import static org.junit.jupiter.api.Assertions.*;


public class NestedObjectArrayTest extends AbstractValidatorTest{

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/nested-objects-arrays.yml";
    }

    @Test
    public void nestedOk()  {
        ValidationErrors errors = validator.validate(Request.post().path("/nested").json().body(getResourceAsStream("/openapi/messages/nested-objects-arrays.json")));
        assertEquals(0,errors.size());
    }

    @Test
    public void nestedInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/nested").json().body(getResourceAsStream("/openapi/messages/nested-objects-arrays-invalid.json")));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/b/2/c/1",e.getContext().getJSONpointer());
        assertEquals("string",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/BODY#/b/2/c/1", e.getContext().getLocationForRequest());
    }
}