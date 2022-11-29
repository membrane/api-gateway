package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static org.junit.Assert.*;


public class NestedObjectArrayTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/nested-objects-arrays.yml"));
    }

    @Test
    public void nestedOk()  {

        ValidationErrors errors = validator.validate(Request.post().path("/nested").json().body(getResourceAsStream("/openapi/nested-objects-arrays.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void nestedInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/nested").json().body(getResourceAsStream("/openapi/nested-objects-arrays-invalid.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/b/2/c/1",e.getValidationContext().getJSONpointer());
        assertEquals("string",e.getValidationContext().getSchemaType());
        assertEquals(400,e.getValidationContext().getStatusCode());
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}