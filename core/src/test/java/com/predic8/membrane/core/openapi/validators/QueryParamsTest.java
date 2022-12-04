package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static org.junit.Assert.*;


public class QueryParamsTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this,"/openapi/query-params.yml"));
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
        assertEquals("limit",e.getContext().getValidatedEntity());
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST/QUERY_PARAMETER/limit", e.getContext().getLocationForRequest());
    }

    @Test
    public void unkownQueryParam() {
        ValidationErrors errors = validator.validate(Request.get().path("/cities?unknown=3&limit=10"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("REQUEST/QUERY_PARAMETER", e.getContext().getLocationForRequest());
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
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("foo",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/QUERY_PARAMETER/foo", e.getContext().getLocationForRequest());
    }
}