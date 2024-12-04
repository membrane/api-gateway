package com.predic8.membrane.core.graphql;

import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class GraphQLoverHttpValidatorTest {

    GraphQLoverHttpValidator validator1;

    @BeforeEach
    void setup() {
        Router router = new Router();
        validator1 = new GraphQLoverHttpValidator(false, Lists.newArrayList("GET", "POST"), 3, 3, 2, router);
    }

    @Test
    void validate() {
        assertDoesNotThrow(() -> {
            validator1.validate(getExchange("""
               {"query":"{a}"}"""));
        });
    }

    @Test
    void wrongOperation() {
        try {
            validator1.validate(getExchange("""
                {
                    "query": "{a}",
                    "operationName": 5
                }"""));
        } catch (GraphQLOverHttpValidationException e) {
            assertEquals("Expected 'operationName' to be a String.", e.getMessage());
            return;
        }
        fail();
    }


    @Test
    public void depthNotOK() throws Exception {
        try {
            validator1.validate(getExchange("""
                 {"query":"{ a { b { c { d { e { f { g { h } } } } } } } }",
                        "operationName": ""}"""));
        } catch (GraphQLOverHttpValidationException e) {
            assertEquals("Max depth exceeded.", e.getMessage());
            return;
        }
        fail();
    }

    private static Exchange getExchange(String body) {
        try {
            return new Request.Builder().post("/graphql").body(body).contentType(APPLICATION_JSON).buildExchange();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}