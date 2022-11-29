package com.predic8.membrane.core.openapi;


import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.sun.mail.imap.protocol.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OpenAPIValidatorObjectTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/customers.yml"));
    }

    @Test
    public void invalidJSON() {

        InputStream is = getResourceAsStream("/openapi/invalid.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(BODY, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("REQUEST", errors.get(0).getValidationContext().getValidatedEntity());

        assertTrue(errors.get(0).toString().contains("cannot be parsed as JSON"));
    }

    @Test
    public void validateRequestBody() {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());

    }

    @Test
    public void invalidRequestBody() {

        InputStream is = getResourceAsStream("/openapi/invalid-customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));

        System.out.println("errors = " + errors);

        assertEquals(3,errors.size());
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getValidatedEntityType().equals(BODY)));
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getValidatedEntity().equals("REQUEST")));
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getStatusCode() == 400));
        //assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getSchemaType().equals("#/components/schemas/Customer")));

        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains("MaxLength")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" maximum ")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" Linden ")));
    }

    @Test
    public void requiredPropertyMissing() {

        InputStream is = getResourceAsStream("/openapi/missing-required-property.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError error = errors.get(0);
        assertTrue(error.getMessage().contains("Required property"));
        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(BODY, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("REQUEST", errors.get(0).getValidationContext().getValidatedEntity());
//        assertEquals("#/components/schemas/Customer",error.getValidationContext().getSchemaType());
    }

    @Test
    public void requiredPropertiesMissing() {

        InputStream is = getResourceAsStream("/openapi/missing-required-properties.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError error = errors.get(0);

        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(BODY, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("REQUEST", errors.get(0).getValidationContext().getValidatedEntity());
        //assertEquals("#/components/schemas/Customer",error.getValidationContext().getSchemaType());

    }

    @Test
    public void additionalPropertiesInvalid() {

        InputStream is = getResourceAsStream("/openapi/customer-additional-properties-invalid.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").json().body(is));

        assertEquals(1,errors.size());
        ValidationError error = errors.get(0);

        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(BODY, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("REQUEST", errors.get(0).getValidationContext().getValidatedEntity());
        //assertEquals("#/components/schemas/Customer",error.getValidationContext().getSchemaType());

    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}