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

public class OpenAPIValidatorJSONSchemaTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/customers.yml";
    }

    @Test
    public void stringInsteadOfIntegerParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/customers/abc"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(400, e.getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, e.getContext().getValidatedEntityType());
        assertEquals("cid", e.getContext().getValidatedEntity());
        assertEquals("/customers/{cid}", e.getContext().getUriTemplate());
        assertEquals("REQUEST/PATH_PARAMETER/cid", e.getContext().getLocationForRequest());
    }

    @Test
    public void floatInsteadOfIntegerPathParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/images/1.0"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(400, e.getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, e.getContext().getValidatedEntityType());
        assertEquals("iid", e.getContext().getValidatedEntity());
        assertEquals("REQUEST/PATH_PARAMETER/iid", e.getContext().getLocationForRequest());
    }

    @Test
    public void minimumNumberPathParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/images/-1"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getContext().getValidatedEntityType());
        assertEquals("iid", errors.get(0).getContext().getValidatedEntity());
    }

    @Test
    public void stringMaxLengthOk() {
        ValidationErrors errors = validator.validate(Request.get().path("/contracts/abcde"));
        assertEquals(0,errors.size());
    }

    @Test
    public void stringMaxLengthToLong() {
        ValidationErrors errors = validator.validate(Request.get().path("/contracts/abcdef"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getContext().getValidatedEntityType());
        assertEquals("cid", errors.get(0).getContext().getValidatedEntity());
    }
}