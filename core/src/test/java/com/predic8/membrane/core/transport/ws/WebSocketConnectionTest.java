package com.predic8.membrane.core.transport.ws;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.transport.ws.WebSocketConnection.computeKeyResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebSocketConnectionTest {

    @Test
    public void testKeyResponse() {
        assertEquals("vvtlPs9jLaZ5KqY6wzvtYznMEpQ=",
                computeKeyResponse("+2chusljI/LtPLXb4+gMZg=="));
    }
}
