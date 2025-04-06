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

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsInterceptorTest {

    CorsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CorsInterceptor();
        interceptor.setAll(false);
        interceptor.setOrigin("https://foo.example");
        interceptor.setMethods("POST, GET, OPTIONS");
        interceptor.setHeaders("X-PINGOTHER, Content-Type");
        interceptor.setMaxAge("86400");
        interceptor.setCredentials(false);
    }


    /**
     * Tests if response contains concrete hostname even if ACAO is *
     *
     * @throws URISyntaxException
     */
    @Test
    void preflight() throws URISyntaxException {
        
        CorsInterceptor i = new CorsInterceptor();
        i.setOrigin("*");
        i.setMethods("POST, GET, OPTIONS");
        
        Exchange exc = Request.options("/foo")
                .header(ORIGIN, "https://foo.example")
                .header(ACCESS_CONTROL_ALLOW_METHODS, "POST")
                .buildExchange();

        assertEquals(RETURN, i.handleRequest(exc));
        
        Response res = exc.getResponse();
        Header hheader = res.getHeader();

        assertEquals(204, res.getStatusCode());

        // Should be same as origin even configuration is *
        assertEquals("https://foo.example", hheader.getFirstValue(ACCESS_CONTROL_ALLOW_ORIGIN));

        System.out.println("hheader.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS) = " + hheader.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS));
//        assertTrue(hheader.getFirstValue(ACCESS_CONTROL_ALLOW_METHODS).contains("POST"));

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

        assertEquals(RETURN, interceptor.handleRequest(preflight));
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