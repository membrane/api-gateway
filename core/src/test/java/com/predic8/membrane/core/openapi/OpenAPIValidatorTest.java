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

package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.METHOD;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH;
import static org.junit.Assert.assertEquals;

public class OpenAPIValidatorTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/customers.yml"));
    }

    @Test
    public void validateSimple() {
        ValidationErrors errors = validator.validate(Request.get().path("/customers"));
        assertEquals(0, errors.size());
    }

    @Test
    public void validateRightMethod() {
        assertEquals(0,validator.validate(Request.get().path("/customers/7")).size());
    }

    @Test
    public void wrongPath() {
        ValidationErrors errors = validator.validate(Request.get().path("/foo"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(404, errors.get(0).getContext().getStatusCode());
        assertEquals(PATH, errors.get(0).getContext().getValidatedEntityType());
    }

    @Test
    public void validateWrongMethod() {
        ValidationErrors errors = validator.validate(Request.patch().path("/customers/7"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(405, errors.get(0).getContext().getStatusCode());
        assertEquals(METHOD, errors.get(0).getContext().getValidatedEntityType());
    }
}