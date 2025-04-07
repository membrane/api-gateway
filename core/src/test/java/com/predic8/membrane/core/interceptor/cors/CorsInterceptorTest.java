package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static org.junit.jupiter.api.Assertions.*;

class CorsInterceptorTest {

    CorsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CorsInterceptor();
        interceptor.setOrigins("https://trusted.example.com");
        interceptor.setMethods("GET, POST, OPTIONS");
        interceptor.setHeaders("Content-Type, Authorization");
        interceptor.setMaxAge("600");
        interceptor.setCredentials(true);
    }

    @Test
    void preflightRequestAllowedOrigin() throws URISyntaxException {
        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .buildExchange();

        assertEquals(RETURN, interceptor.handleRequest(exc));
        Header header = exc.getResponse().getHeader();

        assertEquals(204, exc.getResponse().getStatusCode());
        assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS).contains("POST"));
        assertTrue(header.getFirstValue(ACCESS_CONTROL_ALLOW_HEADERS).contains("Authorization"));
    }

    @Test
    void normalRequestAddsCorsHeaders() throws URISyntaxException {
        Exchange exc = Request.get("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .buildExchange();
        exc.setResponse(Response.ok("Hello").build());

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(CONTINUE, interceptor.handleResponse(exc));

        Header header = exc.getResponse().getHeader();
        assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void requestFromUnauthorizedOriginGetsNoCorsHeaders() throws URISyntaxException {
        Exchange exc = Request.get("/test")
                .header(ORIGIN, "https://evil.example.com")
                .buildExchange();
        exc.setResponse(Response.ok("Nope").build());

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(CONTINUE, interceptor.handleResponse(exc));

        assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightRequestWithoutOriginGetsIgnored() throws URISyntaxException {
        Exchange exc = Request.options("/test").buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void wildcardOriginWithoutCredentialsSetsAsterisk() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("*");
        i.setMethods("GET, POST");
        i.setCredentials(false);

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://any.example.com")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        assertEquals("*", exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void wildcardOriginWithCredentialsThrowsException() {
        CorsInterceptor i = new CorsInterceptor();
        i.setMethods("GET, POST");

        assertThrows(ConfigurationException.class, () -> {
            i.setOrigins("*");
            i.setCredentials(true);
            Exchange exc = Request.options("/test")
                    .header(ORIGIN, "https://my.site")
                    .buildExchange();
            i.handleRequest(exc);
        });
    }

    @Test
    void nonOptionsRequestWithoutOriginIsPassedThrough() throws URISyntaxException {
        Exchange exc = Request.get("/public").buildExchange();
        exc.setResponse(Response.ok("OK").build());

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(CONTINUE, interceptor.handleResponse(exc));

        assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
