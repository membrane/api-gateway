package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.Assert.*;


public class EnumTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/enum.yml"));
    }

    @Test
    public void enumValid() {

        Map<String,String> m = new HashMap<>();
        m.put("state","amber");

        ValidationErrors errors = validator.validate(Request.post().path("/enum").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void enumInvalid() {

        Map<String,String> m = new HashMap<>();
        m.put("state","blue");

        ValidationErrors errors = validator.validate(Request.post().path("/enum").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("enum"));
        assertEquals("REQUEST/BODY#/state", e.getContext().getLocationForRequest());
    }

    /*

    Enum without type is not possible with openapi parser. The parser assumes string!
    https://json-schema.org/understanding-json-schema/reference/generic.html

     */
//    @Test
//    public void readOnlyValid() {
//
//        Map m = new HashMap();
//        m.put("state",42);
//
//        ValidationErrors errors = validator.validate(Request.post().path("/enum-without-type").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
//        assertEquals(0,errors.size());
//    }


    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}