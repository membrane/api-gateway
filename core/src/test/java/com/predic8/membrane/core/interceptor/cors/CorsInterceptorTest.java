package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsInterceptorTest {

    CorsInterceptor corsInterceptor;

    @BeforeEach
    void setUp() {
        corsInterceptor = new CorsInterceptor();
        corsInterceptor.setAllowAll(false);
        corsInterceptor.setAllowOrigin("https://foo.example");
        corsInterceptor.setAllowMethods("POST, GET, OPTIONS");
        corsInterceptor.setAllowHeaders("X-PINGOTHER, Content-Type");
        corsInterceptor.setMaxAge(86400);
        corsInterceptor.setAllowCredentials(false);
    }

    @Test
    void handle() {

        Exchange preflight = new Exchange(null);
        Request preflightReq = new Request();

        preflightReq.setMethod("OPTIONS");
        preflightReq.setUri("/doc");
        preflightReq.setVersion("1.1");
        preflightReq.setHeader(new Header());

        preflightReq.getHeader().add("Origin", "https://foo.example");
        preflightReq.getHeader().add("Access-Control-Request-Method", "POST");
        preflightReq.getHeader().add("Access-Control-Request-Headers", "content-type,x-pingother");

        preflight.setRequest(preflightReq);

        assertEquals(Outcome.ABORT, corsInterceptor.handleRequest(preflight));
        Response preflightResp = preflight.getResponse();
        Header preRespHeader = preflightResp.getHeader();

        assertEquals(204, preflightResp.getStatusCode());
        assertEquals("https://foo.example", preRespHeader.getFirstValue("Access-Control-Allow-Origin"));
        assertTrue(preRespHeader.getFirstValue("Access-Control-Allow-Methods").contains("POST"));
        assertTrue(preRespHeader.getFirstValue("Access-Control-Allow-Headers").toLowerCase().contains("x-pingother"));
        assertEquals("86400", preRespHeader.getFirstValue("Access-Control-Max-Age"));

        Exchange main = new Exchange(null);
        Request mainReq = new Request();
        mainReq.setMethod("POST");
        mainReq.setUri("/doc");
        mainReq.setVersion("1.1");
        mainReq.setHeader(new Header());

        mainReq.getHeader().add("Origin", "https://foo.example");
        mainReq.getHeader().add("X-PINGOTHER", "pingpong");
        mainReq.getHeader().add("Content-Type", "text/xml; charset=UTF-8");
        mainReq.getHeader().add("Cache-Control", "no-cache");
        main.setRequest(mainReq);
        main.setResponse(Response.ok("lorem ipsum").build());

        assertEquals(Outcome.CONTINUE, corsInterceptor.handleRequest(main));
        assertEquals(Outcome.CONTINUE, corsInterceptor.handleResponse(main));
        assertEquals("https://foo.example", main.getResponse().getHeader().getFirstValue("Access-Control-Allow-Origin"));
    }
}