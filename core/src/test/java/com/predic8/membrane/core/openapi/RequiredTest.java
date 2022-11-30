package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.Assert.*;


public class RequiredTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/required.yml"));
    }


    @Test
    public void normalValid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("a",5);
        props.put("b",3);
        props.put("c",6);

        Map<String,Map<String,Integer>> o = new HashMap<>();
        o.put("normal", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(o)));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void normalMissingRequiredInvalid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("a",5);
        props.put("c",6);

        Map<String,Map<String,Integer>> o = new HashMap<>();
        o.put("normal", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(o)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/normal/b", e.getValidationContext().getJSONpointer());
        assertTrue(e.getMessage().contains("Required"));
    }

    @Test
    public void normalMissingMoreRequiredInvalid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("c",6);

        Map<String,Map<String,Integer>> o = new HashMap<>();
        o.put("normal", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(o)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/normal", e.getValidationContext().getJSONpointer());
        assertTrue(e.getMessage().contains("Required"));
        assertTrue(e.getMessage().contains("a,b"));
    }

    @Test
    public void requestRequiredReadOnlyValid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("b",3);
        props.put("c",6);

        Map<String,Map<String,Integer>> readOnlyRequest = new HashMap<>();
        readOnlyRequest.put("read-only-request", props);

        ValidationErrors errors = validator.validate(Request.post().path("/required").body(mapToJson(readOnlyRequest)));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void responseRequiredWriteOnlyValid() {

        Map<String,Integer> props = new HashMap<>();
        props.put("b",3);
        props.put("c",6);

        Map<String,Map<String,Integer>> writeOnlyResponse = new HashMap<>();
        writeOnlyResponse.put("write-only-response", props);

        ValidationErrors errors = validator.validateResponse(Request.get().path("/required"), Response.statusCode(200).mediaType("application/json").body(mapToJson(writeOnlyResponse)));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }



    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}