package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.util.URIFactory;
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
                filter.maskStartLine(new Request.Builder().get("/v1/users?token=abc123&x=1&id_token=XYZ").body("").build()));
    }
}