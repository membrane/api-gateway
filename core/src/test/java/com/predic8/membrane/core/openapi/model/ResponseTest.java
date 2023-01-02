package com.predic8.membrane.core.openapi.model;

import com.predic8.membrane.core.http.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {

    Response res1;

    @BeforeEach
    void setup() throws ParseException {
        res1 = new Response(200, APPLICATION_JSON);
    }

    @Test
    void getType() {
        assertEquals(APPLICATION_JSON, res1.getMediaType().getBaseType());
    }

    @Test
    void match() {
        assertTrue( res1.isOfMediaType(APPLICATION_JSON));
    }
}