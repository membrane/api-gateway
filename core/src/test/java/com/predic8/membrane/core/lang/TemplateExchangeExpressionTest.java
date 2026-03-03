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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.text.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.core.lang.TemplateExchangeExpression.*;
import static com.predic8.membrane.core.lang.TemplateExpressionParser.*;
import static com.predic8.membrane.core.util.text.SerializationFunction.*;
import static com.predic8.membrane.core.util.text.SerializationUtil.Serialization.*;
import static com.predic8.membrane.core.util.xml.XMLTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplateExchangeExpressionTest {

    private static final ObjectMapper om = new ObjectMapper();

    Exchange exc;
    Language language;
    DefaultRouter router;
    TemplateExchangeExpression expression;
    InterceptorAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        exc = get("/foo").header("bar", "42").buildExchange();
        exc.setProperty("prop1", "Mars");
        language = SPEL;
        router = new DefaultRouter();
        adapter = new InterceptorAdapter(router);
        expression = new TemplateExchangeExpression(adapter, language, "aaa", router, TEXT_SERIALIZATION);
    }

    @Test
    void text() {
        assertIterableEquals(List.of(new TemplateExchangeExpression.Text("aaa")), parseTokens(new InterceptorAdapter(router), language, expression.expression));
    }

    @Test
    void simple() {
        assertEquals("foo 6 baz", eval(exc, "foo ${2*3} baz"));
    }

    @Test
    void header() {
        assertEquals("Header: 42", eval(exc, "Header: ${header.bar}"));
    }

    @Test
    void prop() {
        assertEquals("Mars Property", eval(exc, "${property.prop1} Property"));
    }

    @Test
    void multiple() {
        assertEquals("Mars - 42 - 6 7", eval(exc, "${property.prop1} - ${header.bar} - ${2*3} ${7}"));
    }

    @Test
    void encoding() {
        var expr = TemplateExchangeExpression.newInstance(adapter,
                GROOVY,
                "a: ${property.a} b: ${property.b}",
                router,
                URL_SERIALIZATION);
        exc.setProperty("a", "$%&/");
        exc.setProperty("b", "{}a§!");
        assertEquals("a: %24%25%26%2F b: %7B%7Da%C2%A7%21", expr.evaluate(exc, REQUEST, String.class));
    }

    @Nested
    class xpath {

        Exchange xexc;

        @BeforeEach
        void setUp() throws Exception {
            xexc = post("/foo").xml("""
                    <root>
                      <list>
                        <item>Foo</item>
                        <item>Bar</item>
                        <item>Baz</item>
                      </list>
                    </root>
                    """).buildExchange();
        }

        @Test
        void serializeJson() throws JsonProcessingException {
            var list = om.readTree(eval("""
                    {
                        "list": ${//item/text()}
                    }
                    """, SerializationUtil.getSerialization(JSON))).get("list");
            assertEquals(3, list.size());
            assertEquals("Foo", list.get(0).asText());
            assertEquals("Bar", list.get(1).asText());
            assertEquals("Baz", list.get(2).asText());
        }

        @Test
        void serializeXML() throws Exception {
            assertEquals(3,
                    parse(eval("<list>${//item}</list>", SerializationUtil.getSerialization(XML)))
                            .getElementsByTagName("item").getLength());
        }

        @Test
        void serializeText() {
            assertEquals("List: Foo,Bar,Baz",
                    eval("List: ${//item}",
                            SerializationUtil.getSerialization(TEXT)));
        }

        private String eval(String expr, SerializationFunction escaping) {
            return new TemplateExchangeExpression(new InterceptorAdapter(router), XPATH, expr, router, escaping).evaluate(xexc, REQUEST, String.class);
        }

    }

    private String eval(Exchange exc, String expr) {
        return new TemplateExchangeExpression(new InterceptorAdapter(router), language, expr, router, TEXT_SERIALIZATION).evaluate(exc, REQUEST, String.class);
    }
}