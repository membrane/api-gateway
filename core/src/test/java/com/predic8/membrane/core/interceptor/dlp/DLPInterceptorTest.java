package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DLPInterceptorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private DLPInterceptor i;

    @BeforeEach
    void setUp() {
        i = new DLPInterceptor();
    }

    @Test
    void maskFields() throws Exception {
        Mask maskCreditNum = new Mask();
        maskCreditNum.setField("$.credit.number");
        maskCreditNum.setKeepRight("4");

        Mask maskCreditLimit = new Mask();
        maskCreditLimit.setField("$.credit.limit");

        i.setMasks(List.of(maskCreditNum, maskCreditLimit));
        i.init();

        Exchange exc = post("/test")
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body("""
                    {
                      "credit": {
                        "number": "9999999999",
                        "limit": 3000
                      }
                    }
                    """)
                .buildExchange();

        exc.setResponse(Response.ok().build());
        i.handleRequest(exc);
        JsonNode root = OBJECT_MAPPER.readTree(exc.getRequest().getBodyAsStringDecoded());

        assertEquals("******9999", root.path("credit").path("number").asText());
        assertEquals("****", root.path("credit").path("limit").asText());
    }

    @Test
    void filterFields() throws Exception {

        Filter filterCreditNum = new Filter();
        filterCreditNum.setField("$.credit.number");

        Filter filterCreditLimit = new Filter();
        filterCreditLimit.setField("$.credit.limit");

        i.setFilters(List.of(filterCreditNum, filterCreditLimit));
        i.init();

        Exchange exc = post("/test")
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body("""
                    {
                      "credit": {
                        "number": "9999999999",
                        "limit": 3000
                      }
                    }
                    """)
                .buildExchange();

        exc.setResponse(Response.ok().build());
        i.handleRequest(exc);
        JsonNode root = OBJECT_MAPPER.readTree(exc.getRequest().getBodyAsStringDecoded());

        assertEquals("", root.path("credit").path("number").asText());
        assertEquals("", root.path("credit").path("limit").asText());
    }
}
