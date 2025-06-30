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
        Fields fields = new Fields();
        fields.setFields(List.of(
                newField("credit.number", "mask"),
                newField("health_info", "filter")
        ));
        i.setFields(fields);
    }

    @Test
    void shouldMaskAndFilterSensitiveFields() throws Exception {
        Exchange exc = post("/test")
                .header("Content-Type", "application/json")
                .body("""
                        {
                          "person": {
                            "full_name": "Max Mustermann",
                            "health_info": {
                              "status": "undefined",
                              "insurance": {
                                "provider": "AOK",
                                "policy_number": "AOK-123456"
                              }
                            }
                          },
                          "credit": {
                            "number": 1234567890,
                            "limit": 5000,
                            "valid_until": "2027-12"
                          }
                        }
                        """)
                .buildExchange();

        exc.setResponse(Response.ok().body(exc.getRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)).build());
        i.handleResponse(exc);
        String body = exc.getResponse().getBodyAsStringDecoded();
        assertFalse(body.contains("health_info"));
        assertTrue(body.contains("\"number\":\"****\""));
        assertTrue(body.contains("\"valid_until\":\"2027-12\""));
    }

    private Field newField(String name, String action) {
        Field f = new Field();
        f.setName(name);
        f.setAction(action);
        return f;
    }
}
