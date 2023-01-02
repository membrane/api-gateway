package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.isWWWFormUrlEncoded;
import static com.predic8.membrane.core.http.MimeType.isXML;
import static org.junit.jupiter.api.Assertions.*;

class MimeTypeUtilTest {

    @Test
    void isXMLTest() {
        assertTrue(isXML("text/xml"));
        assertTrue(isXML("application/xml"));
        assertTrue(isXML("application/xhtml+xml"));
        assertTrue(isXML("application/xhtml"));
        assertTrue(isXML("application/xhtml"));
        assertTrue(isXML("image/svg+xml"));
        assertTrue(isXML("text/xml-external-parsed-entity"));
    }

    @Test
    void isXWWWFormUrlencoded() {
        assertTrue(isWWWFormUrlEncoded("application/x-www-form-urlencoded"));
    }

}