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
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAPIValidatorTest {

    OpenAPIValidator validator;

    @BeforeEach
    public void setUp() throws IOException {
        OpenAPIRecord rec = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this,"/openapi/specs/customers.yml")),new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), rec);
    }

    @Test
    void validateSimple() {
        ValidationErrors errors = validator.validate(Request.get().path("/customers"));
        assertEquals(0, errors.size());
    }

    @Test
    void validateRightMethod() {
        assertEquals(0,validator.validate(Request.get().path("/customers/7")).size());
    }

    @Test
    void wrongPath() {
        ValidationErrors errors = validator.validate(Request.get().path("/foo"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(404, errors.get(0).getContext().getStatusCode());
        assertEquals(PATH, errors.get(0).getContext().getValidatedEntityType());
    }

    @Test
    void validateWrongMethod() {
        ValidationErrors errors = validator.validate(Request.patch().path("/customers/7"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(405, errors.get(0).getContext().getStatusCode());
        assertEquals(METHOD, errors.get(0).getContext().getValidatedEntityType());
    }
}