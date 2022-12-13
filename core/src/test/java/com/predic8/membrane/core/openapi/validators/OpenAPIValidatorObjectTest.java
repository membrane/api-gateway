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

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OpenAPIValidatorObjectTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/customers.yml"));
    }

    @Test
    public void invalidJSON() {

        InputStream is = getResourceAsStream(this, "/openapi/messages/invalid.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getContext().getStatusCode());
        assertEquals(BODY, errors.get(0).getContext().getValidatedEntityType());
        assertEquals("REQUEST", errors.get(0).getContext().getValidatedEntity());
        assertTrue(errors.get(0).toString().contains("cannot be parsed as JSON"));
    }

    @Test
    public void validateRequestBody() {

        InputStream is = getResourceAsStream(this, "/openapi/messages/customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());

    }

    @Test
    public void invalidRequestBody() {

        InputStream is = getResourceAsStream(this, "/openapi/messages/invalid-customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));

//        System.out.println("errors = " + errors);

        assertEquals(3,errors.size());
        assertTrue(errors.stream().allMatch(ve -> ve.getContext().getValidatedEntityType().equals(BODY)));
        assertTrue(errors.stream().allMatch(ve -> ve.getContext().getValidatedEntity().equals("REQUEST")));
        assertTrue(errors.stream().allMatch(ve -> ve.getContext().getStatusCode() == 400));
        //assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getSchemaType().equals("#/components/schemas/Customer")));

        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains("MaxLength")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" maximum ")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" Linden ")));
    }

    @Test
    public void requiredPropertyMissing() {

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(getResourceAsStream(this, "/openapi/messages/missing-required-property.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("Required property"));
        assertEquals(400, e.getContext().getStatusCode());
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
        assertEquals("REQUEST/BODY#/address/city", e.getContext().getLocationForRequest());
        assertEquals("Customer", e.getContext().getComplexType());
        assertEquals("object", e.getContext().getSchemaType());

//        assertEquals("#/components/schemas/Customer",error.getValidationContext().getSchemaType());
    }

    @Test
    public void requiredPropertiesMissing() {

        InputStream is = getResourceAsStream(this, "/openapi/messages/missing-required-properties.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError error = errors.get(0);

        assertEquals(400, errors.get(0).getContext().getStatusCode());
        assertEquals(BODY, errors.get(0).getContext().getValidatedEntityType());
        assertEquals("REQUEST", errors.get(0).getContext().getValidatedEntity());
    }

    @Test
    public void additionalPropertiesInvalid() {

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(getResourceAsStream(this, "/openapi/messages/customer-additional-properties-invalid.json")));

        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(400, e.getContext().getStatusCode());
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
    }
}