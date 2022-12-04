package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static org.junit.Assert.*;


public class ReadWriteOnlyTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this,"/openapi/read-write-only.yml"));
    }

    @Test
    public void readOnlyValid() {

        Map<String,String> m = new HashMap<>();
        m.put("name","Jack");

        ValidationErrors errors = validator.validate(Request.put().path("/read-only").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void readOnlyInvalid() {

        Map<String,Object> m = new HashMap<>();
        m.put("id",7);
        m.put("name","Jack");

        ValidationErrors errors = validator.validate(Request.put().path("/read-only").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/id",e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("7"));
        assertEquals("REQUEST/BODY/id", e.getContext().getLocationForRequest());
    }

    @Test
    public void writeOnlyInvalid() {

        Map<String,Object> m = new HashMap<>();
        m.put("id",7);
        m.put("name","Jack");
        m.put("role","admin");

        ValidationErrors errors = validator.validateResponse(Request.get().path("/read-only"), Response.statusCode(200).mediaType("application/json").body(mapToJson(m)));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
//        System.out.println("errors = " + errors);
        assertEquals("/role",e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("admin"));
        assertEquals("REQUEST/BODY/role", e.getContext().getLocationForRequest());
    }
}