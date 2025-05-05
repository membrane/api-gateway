package com.predic8.membrane.core.interceptor.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.put;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.JSONPATH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyInterceptorTest {

    private IdempotencyInterceptor interceptor;

    @BeforeEach
    void setup() {
        interceptor = new IdempotencyInterceptor();
        interceptor.setLanguage(String.valueOf(JSONPATH));
        interceptor.setKey("${$.id}");
        interceptor.init();
    }

    @Test
    void newUniqueIdTest() throws URISyntaxException {
        assertEquals(CONTINUE, interceptor.handleRequest(put("").body("{\"id\": \"abc123\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void duplicateIdTest() throws URISyntaxException {
        assertEquals(CONTINUE, interceptor.handleRequest(put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange()));
        assertEquals(ABORT, interceptor.handleRequest(put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void uniqueIdsTest() throws URISyntaxException {
        assertEquals(CONTINUE, interceptor.handleRequest(put("").body("{\"id\": \"789\"}").contentType(APPLICATION_JSON).buildExchange()));
        assertEquals(CONTINUE, interceptor.handleRequest(put("").body("{\"id\": \"987\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void whenIdIsMissingTest() throws URISyntaxException {
        assertEquals(CONTINUE, interceptor.handleRequest(put("").body("{\"msg\": \"Hello World!\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

}
