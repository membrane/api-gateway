package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static org.junit.Assert.*;


public class QueryParamsTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/query-params.yml"));
    }

    @Test
    public void differentTypesOk()  {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?limit=10"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void invalidQueryParam()  {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?limit=200"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("limit",e.getValidationContext().getValidatedEntity());
        assertEquals(QUERY_PARAMETER,e.getValidationContext().getValidatedEntityType());
    }

    @Test
    public void unkownQueryParam() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?unknown=3&limit=10"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
    }

    @Test
    public void missingRequiredParam() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
    }


    @Test
    public void queryParamAtPathLevel()  {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?foo=-1&limit=10"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER,e.getValidationContext().getValidatedEntityType());
        assertEquals("foo",e.getValidationContext().getValidatedEntity());
        assertEquals("integer",e.getValidationContext().getSchemaType());
        assertEquals(400,e.getValidationContext().getStatusCode());

    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}