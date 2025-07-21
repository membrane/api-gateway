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

    @Test
    void shouldOnlyMaskSpecificField() throws Exception {
        i.setMasks(List.of(newMask("$.credit.number")));

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

        exc.setResponse(Response.ok()
                .body(exc.getRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8))
                .build());

        i.handleResponse(exc);

        JsonNode root = OBJECT_MAPPER.readTree(exc.getResponse().getBodyAsStringDecoded());

        assertEquals("****", root.path("credit").path("number").asText());
        assertEquals(3000, root.path("credit").path("limit").asInt());
    }

    private Mask newMask(String jsonPath) {
        Mask mask = new Mask();
        mask.setField(jsonPath);
        return mask;
    }

    private Filter newFilter(String jsonPath) {
        Filter filter = new Filter();
        filter.setField(jsonPath);
        return filter;
    }

}
