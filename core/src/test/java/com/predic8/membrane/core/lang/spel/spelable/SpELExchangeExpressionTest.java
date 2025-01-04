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

package com.predic8.membrane.core.lang.spel.spelable;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.spel.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpELExchangeExpressionTest {

    static Exchange exchange;
    static SpELExchangeExpression excExpression;
    static Interceptor.Flow flow;

    @BeforeAll
    static void setup() throws URISyntaxException {
        exchange = Request.post("/foo")
                .header("foo","bar")
                .body("""
                <person id="7">
                    <name>John Doe</name>
                </person>
                """).buildExchange();
        flow = REQUEST;
    }

    @Test
    void simple() {
        assertEquals("7", eval("7"));
    }

    @Test
    void header() {
        assertEquals("bar", eval("header.foo"));
    }

    String eval(String expression) {
        return ExchangeExpression.getInstance(new Router(), SPEL, expression).evaluate(exchange,flow,String.class);
    }
}
