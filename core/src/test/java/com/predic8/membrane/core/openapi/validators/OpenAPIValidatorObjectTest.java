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
        validator = new OpenAPIValidator(getResourceAsStream(this,"/openapi/customers.yml"));
    }

    @Test
    public void invalidJSON() {

        InputStream is = getResourceAsStream(this,"/openapi/invalid.json");

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

        InputStream is = getResourceAsStream(this,"/openapi/customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());

    }

    @Test
    public void invalidRequestBody() {

        InputStream is = getResourceAsStream(this,"/openapi/invalid-customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));

        System.out.println("errors = " + errors);

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

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(getResourceAsStream(this,"/openapi/missing-required-property.json")));
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

        InputStream is = getResourceAsStream(this,"/openapi/missing-required-properties.json");

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

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(getResourceAsStream(this,"/openapi/customer-additional-properties-invalid.json")));

        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(400, e.getContext().getStatusCode());
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
    }
}