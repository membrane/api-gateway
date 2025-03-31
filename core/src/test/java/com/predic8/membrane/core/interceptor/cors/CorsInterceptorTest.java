package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsInterceptorTest {

    CorsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CorsInterceptor();
        interceptor.setAllowAll(false);
        interceptor.setAllowOrigin("https://foo.example");
        interceptor.setAllowMethods("POST, GET, OPTIONS");
        interceptor.setAllowHeaders("X-PINGOTHER, Content-Type");
        interceptor.setMaxAge("86400");
        interceptor.setAllowCredentials(false);
    }

    @Test
    void handle() throws URISyntaxException {

        Exchange preflight = new Exchange(null);
        preflight.setRequest(new Request.Builder()
                .method(Request.METHOD_OPTIONS)
                .url(new URIFactory(), "http://example.com/doc")
                .header("Origin", "https://foo.example")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .header(ACCESS_CONTROL_ALLOW_HEADERS, "content-type,x-pingother")
                .build());

        assertEquals(Outcome.RETURN, interceptor.handleRequest(preflight));
        Response preflightResp = preflight.getResponse();
        Header preRespHeader = preflightResp.getHeader();

        assertEquals(204, preflightResp.getStatusCode());
        assertEquals("https://foo.example", preRespHeader.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(preRespHeader.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS).contains("POST"));
        assertTrue(preRespHeader.getFirstValue(ACCESS_CONTROL_ALLOW_HEADERS).toLowerCase().contains("x-pingother"));
        assertEquals("86400", preRespHeader.getFirstValue(ACCESS_CONTROL_MAX_AGE));

        Exchange main = new Exchange(null);

        main.setRequest(new Request.Builder()
                .method(Request.METHOD_POST)
                .url(new URIFactory(), "http://example.com/doc")  // Extracts /doc from the URL
                .header("Origin", "https://foo.example")
                .header("X-PINGOTHER", "pingpong")
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("Cache-Control", "no-cache")
                .build());
        main.setResponse(Response.ok("lorem ipsum").build());

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(main));
        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(main));
        assertEquals("https://foo.example", main.getResponse().getHeader().getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}