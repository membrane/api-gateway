package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.METHOD;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH;
import static org.junit.Assert.assertEquals;

public class OpenAPIValidatorTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/customers.yml"));
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
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(405, errors.get(0).getContext().getStatusCode());
        assertEquals(METHOD, errors.get(0).getContext().getValidatedEntityType());
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }
}