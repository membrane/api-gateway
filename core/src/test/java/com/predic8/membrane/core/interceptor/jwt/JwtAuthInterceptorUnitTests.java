package com.predic8.membrane.core.interceptor.jwt;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.*;

import java.util.Map;

import static com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor.*;
import static org.junit.jupiter.api.Assertions.*;

public class JwtAuthInterceptorUnitTests {

    Exchange exc;
    JwtAuthInterceptor interceptor;


    @BeforeEach
    void setup() {
        exc = new Exchange(null);
        interceptor = new JwtAuthInterceptor();
    }

    private String getErrorResponse() {
        return getErrorResponse(exc);
    }

    private String getErrorResponse(Exchange exc) {
        try {
            var map = new ObjectMapper().readValue(exc.getResponse().getBody().toString(), Map.class);
            return map.get("description").toString();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void noJwtInHeader() {
        var exchange = new Request.Builder().buildExchange();
        interceptor.setJwtRetriever(new HeaderJwtRetriever("Authorization", "Bearer"));
        interceptor.handleRequest(exchange);

        assertEquals(ERROR_JWT_NOT_FOUND, getErrorResponse(exchange));
    }

    @Test
    void noJwtGiven() {
        var exception = assertThrows(JWTException.class, () -> interceptor.handleJwt(exc, null));
        assertEquals(ERROR_JWT_NOT_FOUND, exception.getMessage());
    }

    @Test
    void noKidGiven() {
        var token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        var exception = assertThrows(JWTException.class, () -> interceptor.handleJwt(exc, token));
        assertEquals(JsonWebToken.ERROR_JWT_VALUE_NOT_PRESENT("kid"), exception.getMessage());
    }
}
