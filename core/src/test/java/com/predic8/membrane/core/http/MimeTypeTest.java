package com.predic8.membrane.core.http;

import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static org.junit.jupiter.api.Assertions.*;

public class MimeTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"zip","octet-stream"})
    void isBinarySubtypes(String subtype) {
        assertTrue(MimeType.isBinary("foo/" + subtype),subtype);
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio","image","video"})
    void isBinaryPrimaryTypes(String primary) {
        assertTrue(MimeType.isBinary(primary + "/foo"),primary);
    }

    @ParameterizedTest
    @ValueSource(strings = {"xml","xhtml","svg"})
    void isXML(String subtype) {
        assertTrue(MimeType.isXML(  "foo/" + subtype),subtype);
    }
}