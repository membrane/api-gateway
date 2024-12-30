package com.predic8.membrane.core.lang.spel.spelable;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

class SpELExchangeExpressionTest {

    static Exchange exchange;

    @BeforeAll
    static void setup() throws URISyntaxException {
        exchange = Request.post("/foo").body("""
                <person id="7">
                    <name>John Doe</name>
                </person>
                """).buildExchange();
    }
}
