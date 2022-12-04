package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static org.junit.Assert.*;


public class NestedObjectArrayTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this,"/openapi/nested-objects-arrays.yml"));
    }

    @Test
    public void nestedOk()  {
        ValidationErrors errors = validator.validate(Request.post().path("/nested").json().body(getResourceAsStream(this,"/openapi/nested-objects-arrays.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void nestedInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/nested").json().body(getResourceAsStream(this,"/openapi/nested-objects-arrays-invalid.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/b/2/c/1",e.getContext().getJSONpointer());
        assertEquals("string",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/BODY/b/2/c/1", e.getContext().getLocationForRequest());
    }
}