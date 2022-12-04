package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;


public class BooleanTest {

    OpenAPIValidator validator;
    private final static ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/boolean.yml"));
    }

    @Test
    public void validInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/boolean").body(new JsonBody(getBoolean("good",true))));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void inValidInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/boolean").body(new JsonBody(getStrings("good","abc"))));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/good", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("boolean"));
        assertEquals("REQUEST/BODY/good", e.getContext().getLocationForRequest());

    }

    @Test
    public void validInQuery() {
        ValidationErrors errors = validator.validate(Request.get().path("/boolean?truth=true"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void inValidInQuery() {
        ValidationErrors errors = validator.validate(Request.get().path("/boolean?truth=abc"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER, e.getContext().getValidatedEntityType());
        assertEquals("truth", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("boolean"));
        assertEquals("REQUEST/QUERY_PARAMETER/truth", e.getContext().getLocationForRequest());
    }

    private JsonNode getBoolean(String name, boolean bool) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(name,bool);
        return root;
    }

    private JsonNode getStrings(String name, String value) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(name,value);
        return root;
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}