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
package com.predic8.membrane.core.lang.jsonpath;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.lang.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonpathExchangeExpressionTest {

    JsonpathExchangeExpression expression;
    static Router router;
    static Exchange exchange;
    static Flow flow;

    @BeforeAll
    static void setUp() throws URISyntaxException {
        router = new Router();
        exchange = get("/foo").body("""
                {
                    "id": 747,
                    "name": "Jelly Fish"
                }
                """).buildExchange();
        flow = REQUEST;
    }

    @AfterEach
    void tearDown() {
        router.shutdown();
    }

    @Test
    void field() {
        assertEquals("747", eval("$.id")); // TODO null ok?

    }

    @Test
    void accessNonExistingProperty() {
        assertNull(eval("$.unknown")); // TODO null ok?

    }

    String eval(String expression) {
        return ExchangeExpression.getInstance(router, ExchangeExpression.Language.JSONPATH,expression)
                .evaluate(exchange,flow, String.class);
    }
}