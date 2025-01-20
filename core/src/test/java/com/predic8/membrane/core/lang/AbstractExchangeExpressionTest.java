/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;

public abstract class AbstractExchangeExpressionTest {

    protected static Router router;
    protected static Exchange exchange;
    protected static Flow flow;

    @BeforeEach
    void setUp() throws URISyntaxException {
        router = new Router();
        exchange = getRequestBuilder()
                .header("name","Jelly Fish")
                .header("foo","42")
                .header("x-city","Tokio")
                .buildExchange();
        flow = REQUEST;
        exchange.setProperty("wet", true);
        exchange.setProperty("can-fly", false);
        exchange.setProperty("tags", List.of("animal", "water"));
        exchange.setProperty("world", Map.of("country", "US", "continent", "Europe"));
    }

    @AfterAll
    static void tearDown() {
        router.shutdown();
    }

    protected abstract Request.Builder getRequestBuilder() throws URISyntaxException;

    protected abstract Language getLanguage();

    protected Object evalObject(String expression) {
        return ExchangeExpression.getInstance(router, getLanguage(),expression)
                .evaluate(exchange,flow, Object.class);
    }

    protected boolean evalBool(String expression) {
        return ExchangeExpression.getInstance(router, getLanguage(),expression)
                .evaluate(exchange,flow, Boolean.class);
    }

    protected String evalString(String expression) {
        return ExchangeExpression.getInstance(router, getLanguage(),expression)
                .evaluate(exchange,flow, String.class);
    }
}