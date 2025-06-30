package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.*;

class DLPInterceptorTest {

    private DLPInterceptor i;

    @BeforeEach
    void setUp() {
        i = new DLPInterceptor();
        i.init();
    }

    @Test
    void shouldOnlyMaskSpecificField() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField("credit.number", "mask")
        )));

        Exchange exc = post("/test")
                .header("Content-Type", "application/json")
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
        String body = exc.getResponse().getBodyAsStringDecoded();

        assertTrue(body.contains("\"number\":\"****\""));
        assertTrue(body.contains("\"limit\":3000"));
    }

    @Test
    void shouldCompletelyFilterNestedBranch() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField("person.health_info", "filter")
        )));

        Exchange exc = post("/test")
                .header("Content-Type", "application/json")
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
        String body = exc.getResponse().getBodyAsStringDecoded();

        assertFalse(body.contains("health_info"));
        assertTrue(body.contains("full_name"));
    }

    @Test
    void shouldFilterAllMatchingFieldNames() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField(".*status.*", "filter")
        )));

        Exchange exc = post("/test")
                .header("Content-Type", "application/json")
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
        String body = exc.getResponse().getBodyAsStringDecoded();

        assertFalse(body.contains("status"));
        assertTrue(body.contains("delivery"));
    }

    @Test
    void shouldLeaveUnconfiguredFieldsUntouched() throws Exception {
        i.setFields(new Fields().setFields(List.of(
                newField("credit.number", "mask")
        )));

        Exchange exc = post("/test")
                .header("Content-Type", "application/json")
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
        String body = exc.getResponse().getBodyAsStringDecoded();

        assertTrue(body.contains("something"));
        assertTrue(body.contains("unrelated"));
    }

    private Field newField(String name, String action) {
        Field f = new Field();
        f.setName(name);
        f.setAction(action);
        return f;
    }
}
