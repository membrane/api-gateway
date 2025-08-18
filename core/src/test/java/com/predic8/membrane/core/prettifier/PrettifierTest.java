package com.predic8.membrane.core.prettifier;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.prettifier.Prettifier.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PrettifierTest {

    @Test
    void json() {
        assertEquals(JSON, getInstance(APPLICATION_JSON));
        assertEquals(JSON, getInstance(APPLICATION_JSON_UTF8));
        assertEquals(JSON, getInstance(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void xml() {
        assertEquals(XML, getInstance(APPLICATION_XML));
        assertEquals(XML, getInstance(TEXT_XML));
        assertEquals(XML, getInstance(TEXT_XML_UTF8));
    }

    @Test
    void text() {
        assertEquals(TEXT, getInstance(TEXT_PLAIN));
        assertEquals(TEXT, getInstance(TEXT_PLAIN_UTF8));
        assertEquals(TEXT, getInstance("trash"));
    }

}