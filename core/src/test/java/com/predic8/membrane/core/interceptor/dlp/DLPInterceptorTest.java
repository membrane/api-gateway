package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.*;

class DLPInterceptorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private DLPInterceptor i;

    @BeforeEach
    void setUp() {
        i = new DLPInterceptor();
        i.init();
    }

    /*@Test
    void shouldOnlyMaskSpecificField() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField("credit.number", "mask")
        )));

        Exchange exc = post("/test")
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body("""
                        {
                          "credit": {
                            "number": 9999999999,
                            "limit": 3000
                          }
                        }
                        """)
                .buildExchange();

        exc.setResponse(Response.ok().body(exc.getRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)).build());
        i.handleResponse(exc);
        JsonNode root = OBJECT_MAPPER.readTree(exc.getResponse().getBodyAsStringDecoded());

        assertEquals("****", root.path("credit").path("number").asText());
        assertEquals(3000, root.path("credit").path("limit").asInt());
    }

    @Test
    void shouldCompletelyFilterNestedBranch() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField("person.health_info", "filter")
        )));

        Exchange exc = post("/test")
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body("""
                        {
                          "person": {
                            "full_name": "Max",
                            "health_info": {
                              "status": "ok"
                            }
                          }
                        }
                        """)
                .buildExchange();

        exc.setResponse(Response.ok().body(exc.getRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)).build());
        i.handleResponse(exc);

        JsonNode root = OBJECT_MAPPER.readTree(exc.getResponse().getBodyAsStringDecoded());

        assertTrue(root.path("person").has("full_name"));
        assertFalse(root.path("person").has("health_info"));
    }

    @Test
    void shouldFilterAllMatchingFieldNames() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField(".*status.*", "filter")
        )));

        Exchange exc = post("/test")
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body("""
                        {
                          "health": {
                            "status": "red"
                          },
                          "delivery": {
                            "status_code": 200
                          }
                        }
                        """)
                .buildExchange();

        exc.setResponse(Response.ok().body(exc.getRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)).build());
        i.handleResponse(exc);
        JsonNode root = OBJECT_MAPPER.readTree(exc.getResponse().getBodyAsStringDecoded());

        assertFalse(root.path("health").has("status"));
        assertFalse(root.path("delivery").has("status_code"));
    }

    @Test
    void shouldLeaveUnconfiguredFieldsUntouched() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField("credit.number", "mask")
        )));

        Exchange exc = post("/test")
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body("""
                        {
                          "something": {
                            "unrelated": true
                          }
                        }
                        """)
                .buildExchange();

        exc.setResponse(Response.ok().body(exc.getRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)).build());
        i.handleResponse(exc);
        JsonNode root = OBJECT_MAPPER.readTree(exc.getResponse().getBodyAsStringDecoded());

        assertTrue(root.has("something"));
        assertTrue(root.path("something").has("unrelated"));
    }

    private Field newField(String name, String action) {
        Field f = new Field();
       *//* f.setName(name);
        f.setAction(action);*//*
        return f;
    }*/
}
