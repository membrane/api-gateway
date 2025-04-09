package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
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
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        assertEquals(204, exc.getResponse().getStatusCode());
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
        assertEquals(403, exc.getResponse().getStatusCode());
    }

    @Test
    void restrictToRequested() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("*");
        i.setMethods("GET, POST");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        Header header = exc.getResponse().getHeader();
        assertEquals(204, exc.getResponse().getStatusCode());
        assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("POST", header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    void preflightRequestAllowedOrigin() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST");
        i.setHeaders(CONTENT_TYPE + ", Authorization");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .header(ACCESS_CONTROL_ALLOW_HEADERS, "Authorization")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        Header header = exc.getResponse().getHeader();

        assertEquals(204, exc.getResponse().getStatusCode());
        assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(header.getFirstValue(ACCESS_CONTROL_ALLOW_HEADERS).contains("Authorization"));
    }

    @Test
    void normalRequestAddsCorsHeaders() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST");

        Exchange exc = Request.get("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();
        exc.setResponse(Response.ok("Hello").build());

        assertEquals(CONTINUE, i.handleRequest(exc));
        assertEquals(CONTINUE, i.handleResponse(exc));

        Header header = exc.getResponse().getHeader();
        assertEquals("https://trusted.example.com", header.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("POST", header.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
        assertNull(header.getFirstValue(ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void requestFromUnauthorizedOriginGetsNoCorsHeaders() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST");

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
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        assertEquals("https://any.example.com", exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
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
                    .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                    .buildExchange();
            i.handleRequest(exc);
        });
    }

    @Test
    void shouldReturnOnlyRequestedMethodInCorsResponse() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();

        i.handleRequest(exc);

        assertEquals("POST", exc.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    void shouldAllowNullOriginWhenExplicitlyConfigured() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("http://localhost:5173/ null");
        i.setMethods("GET, POST");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "null")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();
        i.handleRequest(exc);

        assertEquals(204, exc.getResponse().getStatusCode());
    }

    @Test
    void shouldRejectRequestWithDisallowedHeaders() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET, POST");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .header(ACCESS_CONTROL_ALLOW_HEADERS, "Foo")
                .buildExchange();

        i.handleRequest(exc);

        assertEquals(403, exc.getResponse().getStatusCode());
    }

    @Test
    void shouldRejectRequestWithDisallowedMethod() throws URISyntaxException {
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigins("https://trusted.example.com");
        i.setMethods("GET");

        Exchange exc = Request.options("/test")
                .header(ORIGIN, "https://trusted.example.com")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();

        i.handleRequest(exc);

        assertEquals(403, exc.getResponse().getStatusCode());
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
