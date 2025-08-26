package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Header.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskSensitiveTest {

    @Test
    void masksSensitiveHeaders() {
        Header header = new Header();
        String authVal = "Basic dGVzdDp0ZXN0";
        String cookieVal = "foo=bar";
        String apiKeyVal = "abc123";
        String contentTypeVal = "application/json";


        header.add(AUTHORIZATION, authVal);
        header.add(COOKIE, cookieVal);
        header.add("X-Api-Key", apiKeyVal);
        header.add(CONTENT_TYPE, contentTypeVal);

        String masked = MaskSensitive.mask(header);

        assertTrue(masked.contains(AUTHORIZATION + ": " + stars(authVal)));
        assertTrue(masked.contains(COOKIE + ": " + stars(cookieVal)));
        assertTrue(masked.contains("X-Api-Key: " + stars(apiKeyVal)));
        assertTrue(masked.contains(CONTENT_TYPE + ": " + contentTypeVal));
    }

    @Test
    void caseInsensitiveNames() {
        Header header = new Header();
        String authVal = "secret";
        header.add("aUtHoRiZaTiOn", authVal);

        assertTrue(MaskSensitive.mask(header).contains("aUtHoRiZaTiOn: " + "*".repeat(authVal.length())));
    }

    @Test
    void emptyValues() {
        Header header = new Header();
        header.add(AUTHORIZATION, "");
        header.add(COOKIE, "");

        String masked = MaskSensitive.mask(header);
        assertTrue(masked.contains(AUTHORIZATION + ": "));
        assertTrue(masked.contains(COOKIE + ": "));
    }

    private static String stars(String s) {
        return s == null ? "" : "*".repeat(s.length());
    }
}