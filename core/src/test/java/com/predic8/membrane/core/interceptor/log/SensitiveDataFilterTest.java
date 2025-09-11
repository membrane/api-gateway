package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.Header.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveDataFilterTest {

    SensitiveDataFilter filter;
    Header header;

    @BeforeEach
    void setUp() {
        filter = new SensitiveDataFilter();
        header = new Header();
    }

    @Test
    void masksSensitiveValues() {

        String contentTypeVal = "application/json";

        header.setValue(AUTHORIZATION, "Basic dGVzdDp0ZXN0");
        header.setValue(COOKIE, "foo=bar");
        header.setValue("X-Api-Key", "abc123");
        header.setValue(CONTENT_TYPE, contentTypeVal);

        Header masked = filter.getMaskedHeader(header);

        assertEquals("******************", masked.getAuthorization());
        assertEquals("*******", masked.getFirstValue(COOKIE));
        assertEquals("******", masked.getFirstValue("X-Api-Key"));
        assertEquals(contentTypeVal, masked.getContentType());
    }

    @Test
    void maskStartLine() throws URISyntaxException {
        assertEquals("GET /v1/users?token=********&x=1&id_token=******** HTTP/1.1\r\n",
                filter.maskStartLine(Request.get("/v1/users?token=abc123&x=1&id_token=XYZ").body("").build()));

        assertEquals("GET /v1/res?api-key=********&api_key=********&x-api-key=******** HTTP/1.1\r\n",
                filter.maskStartLine(Request.get("/v1/res?api-key=A&api_key=B&x-api-key=C").build()));

        assertEquals("GET /q?password=********&keep=ok HTTP/1.1\r\n",
                filter.maskStartLine(Request.get("/q?password=&keep=ok").build()));

        assertEquals("GET /v1/oauth?client_secret=********&keep=ok HTTP/1.1\r\n",
                filter.maskStartLine(Request.get("/v1/oauth?client_secret=mySecret123&keep=ok").build())
        );

        assertEquals("GET /v1/auth?access_token=********&keep=ok HTTP/1.1\r\n",
                filter.maskStartLine(Request.get("/v1/auth?access_token=superSecret456&keep=ok").build())
        );
    }
}