package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Header.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveDataFilterTest {

    @Test
    void masksSensitiveValues() {

        SensitiveDataFilter filter = new SensitiveDataFilter();
        Header header = new Header();
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
    void maskingOfNullValuesWorks() {
        SensitiveDataFilter filter = new SensitiveDataFilter();
        Header header = new Header();
        header.setValue(AUTHORIZATION, "abc123");

        assertEquals("******", filter.getMaskedHeader(header).getAuthorization());
        assertEquals("abc123".length(), header.getAuthorization().length());
    }
}