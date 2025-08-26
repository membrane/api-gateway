package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Header.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskSensitiveTest {

    @Test
    void masksSensitiveHeaders() {
        Header header = new Header();
        String authVal = "Basic dGVzdDp0ZXN0";
        String cookieVal = "foo=bar";
        String apiKeyVal = "abc123";
        String contentTypeVal = "application/json";


        header.setValue(AUTHORIZATION, authVal);
        header.setValue(COOKIE, cookieVal);
        header.setValue(COOKIE, "bar=baz");
        header.setValue("X-Api-Key", apiKeyVal);
        header.setValue(CONTENT_TYPE, contentTypeVal);

        Header masked = MaskSensitive.mask(header);

        assertEquals(stars(authVal), masked.getAuthorization());
        assertEquals(stars(cookieVal), masked.getFirstValue(COOKIE));
        assertEquals(stars(apiKeyVal), masked.getFirstValue("X-Api-Key"));
        assertEquals(contentTypeVal, masked.getContentType());
    }

    @Test
    void caseInsensitiveNames() {
        Header header = new Header();
        String authVal = "secret";
        header.setValue("aUtHoRiZaTiOn", authVal);

        assertTrue(MaskSensitive.mask(header).toString()
                .contains("aUtHoRiZaTiOn: " + "*".repeat(authVal.length())));
    }

    @Test
    void emptyValues() {
        Header header = new Header();
        header.setValue(AUTHORIZATION, "");
        header.setValue(COOKIE, "");

        Header masked = MaskSensitive.mask(header);
        assertTrue(masked.toString().contains(header.getAuthorization()));
        assertTrue(masked.toString().contains(header.getFirstValue(COOKIE)));

    }

    private static String stars(String s) {
        return s == null ? "" : "*".repeat(s.length());
    }
}