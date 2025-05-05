package com.predic8.membrane.core.interceptor.idempotency;

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.put;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.JSONPATH;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyInterceptorTest {

    private IdempotencyInterceptor i;

    @BeforeEach
    void setup() {
        i = new IdempotencyInterceptor();
        i.setLanguage(JSONPATH);
        i.setKey("$.id");
        i.init();
    }

    @Test
    void newUniqueIdTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"abc123\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void duplicateIdTest() throws URISyntaxException {
        Exchange firstExchange = put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange();
        Exchange secondExchange = put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange();
        assertEquals(CONTINUE, i.handleRequest(firstExchange));
        assertEquals(ABORT, i.handleRequest(secondExchange));
        assertEquals(400, secondExchange.getResponse().getStatusCode());
    }

    @Test
    void uniqueIdsTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"789\"}").contentType(APPLICATION_JSON).buildExchange()));
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"987\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void whenIdIsMissingTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"msg\": \"Hello World!\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void defaultLanguageTest() {
        assertEquals(SPEL, new IdempotencyInterceptor().getLanguage());
    }
}
