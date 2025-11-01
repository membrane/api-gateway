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
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.lang.TemplateExchangeExpression.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplateExchangeExpressionTest {

    static Exchange exc;
    static Language language;
    static Router router;

    @BeforeAll
    static void setUp() throws Exception {
        exc = Request.get("/foo")
                .header("bar", "42")
                .buildExchange();
        exc.setProperty("prop1", "Mars");
        language = Language.SPEL;
        router = new Router();
    }

    @Test
    void text() {
        assertIterableEquals(List.of(new Text("aaa")), parseTokens(new InterceptorAdapter(router),language,"aaa"));
    }

    @Test
    void simple() {
        assertEquals("foo 6 baz", eval("foo ${2*3} baz"));
    }

    @Test
    void header() {
        assertEquals("Header: 42", eval("Header: ${header.bar}"));
    }

    @Test
    void prop() {
        assertEquals("Mars Property", eval("${property.prop1} Property"));
    }

    @Test
    void multiple() {
        assertEquals("Mars - 42 - 6 7", eval("${property.prop1} - ${header.bar} - ${2*3} ${7}"));
    }

    private static String eval(String expr) {
        return new TemplateExchangeExpression(new InterceptorAdapter(router), language, expr).evaluate(exc, REQUEST,String.class);
    }
}