/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccessLogInterceptorTest {

    AccessLogInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new AccessLogInterceptor();

        var variables = new ArrayList<AdditionalVariable>();
        AdditionalVariable av1 = new AdditionalVariable();
        av1.setName("foo");
        av1.setExpression("headers.foo");
        AdditionalVariable av2 = new AdditionalVariable();
        av2.setExpression("headers['X-Forwarded-For']");
        av2.setName("Forwarded");
        AdditionalVariable av3 = new AdditionalVariable();
        av3.setName("orderId");
        av3.setExpression("jsonPath('$.orderId')");
        variables.add(av1);
        variables.add(av2);
        variables.add(av3);

        interceptor.setAdditionalPatternList(variables);
        interceptor.init();
    }

    @Test
    void simple() throws Exception {
        interceptor.handleResponse(Request.get("/foo").header("foo","bar").header("X-Forwarded-For","bazf").buildExchange());
    }

    @Test
    void userProvidedValuesAreEscaped() throws Exception {
        var mdc = captureMDC(Request.post("/orders")
                .json("""
                        {"orderId":"A-1\\n10.0.0.1 \\"GET /admin\\"\\r\\u001b[31m\\\\"}""")
                .buildExchange());

        assertEquals("A-1\\n10.0.0.1 \\\"GET /admin\\\"\\r\\u001b[31m\\\\", mdc.get("orderId"));
    }

    @Test
    void timestampPatternCanFollowCommonLogFormat() throws Exception {
        interceptor.setDateTimePattern("dd/MMM/yyyy:HH:mm:ss Z");
        interceptor.init();
        var exc = Request.get("/foo").buildExchange();
        exc.setTimeReqReceived(1759393665000L);

        assertTrue(captureMDC(exc).get("time.req.received.format").matches("\\d{2}/Oct/2025:\\d{2}:\\d{2}:\\d{2} [+-]\\d{4}"));
    }

    private Map<String, String> captureMDC(com.predic8.membrane.core.exchange.Exchange exc) {
        var captured = new CopyOnWriteArrayList<Map<String, String>>();
        var appender = new AbstractAppender("AccessLogInterceptorTest", null, null, false, null) {
            @Override
            public void append(LogEvent event) {
                captured.add(event.getContextData().toMap());
            }
        };
        appender.start();
        var logger = (Logger) LogManager.getLogger("com.predic8.membrane.core.interceptor.log.access.AccessLogInterceptorService");
        logger.addAppender(appender);
        try {
            interceptor.handleResponse(exc);
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        assertEquals(1, captured.size());
        return captured.getFirst();
    }
}
