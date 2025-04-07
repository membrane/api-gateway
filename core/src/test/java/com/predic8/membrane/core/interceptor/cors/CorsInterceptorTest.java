package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static org.junit.jupiter.api.Assertions.*;

class CorsInterceptorTest {

    @Test
    void parseOriginSpaces() {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("foo bar baz");
        assertEquals(List.of("foo", "bar", "baz"), i.getAllowedOrigins());
    }

    @Test
    void parseMethodSpaces() {
        CorsInterceptor i = new CorsInterceptor();
        i.setMethods("GET, POST");
        assertEquals(List.of("GET", "POST"), i.getMethods());
    }

    @Test
    void originNullWorking() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("foo bar null");
        i.setMethods("POST, GET");
        Exchange exc = Request.options("/test")
                .header(ORIGIN, "null")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        Response response = exc.getResponse();
        assertEquals(204, response.getStatusCode());
    }

    @Test
    void originNullNotWorking() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("foo bar");
        i.setMethods("POST, GET");
        Exchange exc = Request.options("/test")
                .header(ORIGIN, "null")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        Response response = exc.getResponse();
        assertEquals(403, response.getStatusCode());
    }

    @Test
    void restrictToRequested() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("*");
        i.setMethods("GET, POST, OPTIONS");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        Header header = exc.getResponse().getHeader();

        assertEquals(204, exc.getResponse().getStatusCode());
        assertEquals("*", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("GET POST OPTIONS", header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    void preflightRequestAllowedOrigin() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST, OPTIONS");
        i.setHeaders("Content-Type, Authorization");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        Header header = exc.getResponse().getHeader();

        assertEquals(204, exc.getResponse().getStatusCode());
        assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS).contains("OPTIONS"));
        assertTrue(header.getFirstValue(ACCESS_CONTROL_ALLOW_HEADERS).contains("Authorization"));
    }

    @Test
    void normalRequestAddsCorsHeaders() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST, OPTIONS");
        i.setHeaders("Content-Type, Authorization");

        Exchange exc = Request.get("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .buildExchange();
        exc.setResponse(Response.ok("Hello").build());

        assertEquals(CONTINUE, i.handleRequest(exc));
        assertEquals(CONTINUE, i.handleResponse(exc));

        Header header = exc.getResponse().getHeader();
        assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("GET, POST, OPTIONS", header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
        assertFalse(header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS).contains("PUT"));
        assertTrue(header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS).contains("GET"));
        assertNull(header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void requestFromUnauthorizedOriginGetsNoCorsHeaders() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST, OPTIONS");
        i.setHeaders("Content-Type, Authorization");

        Exchange exc = Request.get("/test")
                .header(ORIGIN, "https://evil.example.com")
                .buildExchange();
        exc.setResponse(Response.ok("Nope").build());

        assertEquals(CONTINUE, i.handleRequest(exc));
        assertEquals(CONTINUE, i.handleResponse(exc));

        assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightRequestWithoutOriginGetsIgnored() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        Exchange exc = Request.options("/test").buildExchange();

        assertEquals(CONTINUE, i.handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void wildcardOriginWithoutCredentialsSetsAsterisk() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("*");
        i.setMethods("GET, POST");

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
        assertThrows(ConfigurationException.class, () -> {
            i.setOrigins("*");
            i.setMethods("GET, POST");
            i.setCredentials(true);
            Exchange exc = Request.options("/test")
                    .header(ORIGIN, "https://my.site")
                    .buildExchange();
            i.handleRequest(exc);
        });
    }

    @Test
    void nonOptionsRequestWithoutOriginIsPassedThrough() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        Exchange exc = Request.get("/public").buildExchange();
        exc.setResponse(Response.ok("OK").build());
        assertEquals(CONTINUE, i.handleRequest(exc));
        assertEquals(CONTINUE, i.handleResponse(exc));
        assertNull(exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
