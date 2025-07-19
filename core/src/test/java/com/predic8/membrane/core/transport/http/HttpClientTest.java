package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {

    HttpClient client;

    @BeforeEach
    void setUp() {
        client = new HttpClient();
    }

    @Test
    void init() throws Exception {
        Exchange exc = Request.get("/foo").buildExchange();
        client.initializeRequest(exc,"https://example.com",true);
        assertEquals("example.com:443",exc.getRequest().getHeader().getHost());
    }

    @Test
    void setRequestURISimple() throws Exception {
        var request = Request.get("/bar").build();
        client.setRequestURI(request, "https://predic8.de/foo");
        assertEquals("/foo", request.getUri());
    }

    @Test
    void setRequestURIWithoutPath() throws Exception {
        var request = Request.get("/bar").build();
        client.setRequestURI(request, "https://predic8.de");
        assertEquals("/", request.getUri());
    }
}