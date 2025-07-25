package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class Http1ProtocolHandlerTest {

    @Test
    void connectWithoutProxy() throws Exception {
        Http1ProtocolHandler handler = new Http1ProtocolHandler(new HttpClientConfiguration());
        Exchange exchange = handler.handle(new Request.Builder().buildExchange(),null, null);
        System.out.println(exchange);

    }

}