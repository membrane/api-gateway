package com.predic8.membrane.core.openapi;


import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;

public class ResponseTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/customers.yml"));
    }

    @Test
    public void validCustomerResponse() throws FileNotFoundException {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).body(is));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
//        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
//        assertEquals(BODY, errors.get(0).getValidationContext().getValidatedEntityType());
//        assertEquals("REQUEST", errors.get(0).getValidationContext().getValidatedEntity());
//
//        assertTrue(errors.get(0).toString().contains("cannot be parsed as JSON"));
    }

    @Test
    public void invalidCustomerResponse() throws FileNotFoundException {

        InputStream is = getResourceAsStream("/openapi/invalid-customer.json");

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).body(is));

//        System.out.println("errors = " + errors);

        assertEquals(3,errors.size());
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getValidatedEntityType().equals(BODY)));
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getValidatedEntity().equals("RESPONSE")));
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getStatusCode() == 400));
   //     assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getSchemaType().equals("#/components/schemas/Customer")));

        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains("MaxLength")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" maximum ")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" Linden ")));
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}