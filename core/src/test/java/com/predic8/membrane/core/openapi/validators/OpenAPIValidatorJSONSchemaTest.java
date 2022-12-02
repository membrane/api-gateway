package com.predic8.membrane.core.openapi.validators;


import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH_PARAMETER;
import static org.junit.Assert.assertEquals;

public class OpenAPIValidatorJSONSchemaTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/customers.yml"));
    }

    @Test
    public void stringInsteadOfIntegerParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/customers/abc"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("cid", errors.get(0).getValidationContext().getValidatedEntity());
        assertEquals("/customers/{cid}", errors.get(0).getValidationContext().getUriTemplate());
    }

    @Test
    public void floatInsteadOfIntegerPathParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/images/1.0"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("iid", errors.get(0).getValidationContext().getValidatedEntity());

    }

    @Test
    public void minimumNumberPathParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/images/-1"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("iid", errors.get(0).getValidationContext().getValidatedEntity());
    }

    @Test
    public void stringMaxLengthOk() {
        ValidationErrors errors = validator.validate(Request.get().path("/contracts/abcde"));
        assertEquals(0,errors.size());
    }

    @Test
    public void stringMaxLengthToLong() {
        ValidationErrors errors = validator.validate(Request.get().path("/contracts/abcdef"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getValidationContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getValidationContext().getValidatedEntityType());
        assertEquals("cid", errors.get(0).getValidationContext().getValidatedEntity());
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }
}