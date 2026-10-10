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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.lang.AbstractExchangeExpressionTest;
import com.predic8.membrane.core.lang.ExchangeExpression.InterceptorAdapter;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.JSONPATH;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;
import static java.lang.Boolean.FALSE;
import static org.junit.jupiter.api.Assertions.*;

class JsonpathExchangeExpressionTest extends AbstractExchangeExpressionTest {

    @Override
    protected Language getLanguage() {
        return JSONPATH;
    }

    @Override
    protected Request.Builder getRequestBuilder() throws URISyntaxException {
        return post("/foo?city=Paris")
                .body("""
                {
                    "id": 747,
                    "name": "Jelly Fish",
                    "fish": true,
                    "insect": false,
                    "wings": null,
                    "tags": ["animal","water"],
                    "world": {
                        "country": "US",
                        "continent": "Europe"
                    },
                    "numbers": [1,2,3],
                    "objects": [
                        {"foo": "bar"},
                        {"foo": "baz"},
                        {"foo": "qux"}
                    ]
                }
                """);
    }

    @Test
    void field() {
        assertEquals("747", evalString("$.id"));
    }

    @Test
    void accessNonExistingProperty() {
        assertNull(evalString("$.unknown"));
    }

    @Test
    void truth() {
        assertTrue(evalBool("$.id"));
        assertTrue(evalBool("$.fish"));
        assertFalse(evalBool("$.insect"));
        assertFalse(evalBool("$.wings"));
    }

    @Test
    void filterWithoutMatchIsFalse() throws URISyntaxException {
        assertFalse(evalBool("$.animals[?(@.species == 'cat')]", """
                {"animals":[{"species":"dog"}]}"""));
    }

    @Test
    void filterWithMatchIsTrue() throws URISyntaxException {
        assertTrue(evalBool("$.animals[?(@.species == 'cat')]", """
                {"animals":[{"species":"dog"},{"species":"cat"}]}"""));
    }

    @Test
    void wildcardWithoutMatchIsFalse() throws URISyntaxException {
        assertFalse(evalBool("$.animals[*].name", """
                {"animals":[]}"""));
    }

    @Test
    void existingEmptyArrayIsTrue() throws URISyntaxException {
        // A definite path selects the property itself, so an existing but empty array still counts
        assertTrue(evalBool("$.animals", """
                {"animals":[]}"""));
    }

    @Test
    void evalString() {
        assertEquals("747", evalString("$.id"));
        
        var tags = evalString("$.tags");
        assertTrue(tags.contains("animal"));
        assertTrue(tags.contains("water"));

        // The behavior is different from XPath where you get the value of the first node as String. But
        // it is consistent with most JSONPath implementations.
        var numbes = evalString("$.numbers");
        assertTrue(numbes.contains("1"));
        assertTrue(numbes.contains("2"));
        assertTrue(numbes.contains("3"));

        var objects = evalString("$.objects");
        assertTrue(objects.contains("foo"));
        assertTrue(objects.contains("baz"));
        assertTrue(objects.contains("qux"));
    }

    @Test
    void list() {
        Object o = evalObject("$.tags");
        if (!(o instanceof List<?> l)) {
            fail();
            return;
        }
        assertEquals(2, l.size());
        assertEquals("animal", l.get(0));
        assertEquals("water", l.get(1));
    }

    @Test
    void map() {
        Object o = evalObject("$.world");
        if (!(o instanceof Map<?,?> m)) {
            fail();
            return;
        }
        assertEquals("US",m.get("country"));
        assertEquals("Europe",m.get("continent"));
    }

    @Test
    void emptyBodyForObject() throws URISyntaxException {
        // Same as a JSON body without the field
        assertEquals(expression(new InterceptorAdapter(router), JSONPATH, "$.a").evaluate(post("/foo").json("{}").buildExchange(), REQUEST, Object.class),
                evaluateWithEmptyBodyFor(Object.class));
    }

    @Test
    void emptyBodyForString() throws URISyntaxException {
        assertNull(evaluateWithEmptyBodyFor(String.class));
    }

    @Test
    void emptyBodyForBoolean() throws URISyntaxException {
        assertEquals(FALSE, evaluateWithEmptyBodyFor(Boolean.class));
    }

    @Test
    void wrongContentType() throws URISyntaxException {
        assertNull(expression(new InterceptorAdapter(router), JSONPATH, "$.a")
                .evaluate(post("/foo").contentType(TEXT_XML).body("<a/>").buildExchange(), REQUEST, String.class));
    }

    @Nested
    class Body {

        @Test
        void textPlainIsParsed() throws URISyntaxException {
            assertTrue(eval("$.a", post("/foo").contentType(TEXT_PLAIN).body("{\"a\":1}").buildExchange()));
        }

        @Test
        void missingContentTypeIsParsed() throws URISyntaxException {
            assertTrue(eval("$.a", post("/foo").body("{\"a\":1}").buildExchange()));
        }

        @Test
        void formDataWithJsonIsParsed() throws URISyntaxException {
            assertTrue(eval("$.a", post("/foo").contentType(APPLICATION_X_WWW_FORM_URLENCODED).body("{\"a\":1}").buildExchange()));
        }

        @Test
        void formDataThatIsNotJsonIsAnEmptyDocument() throws URISyntaxException {
            assertFalse(eval("$.a", post("/foo").contentType(APPLICATION_X_WWW_FORM_URLENCODED).body("a=1&b=2").buildExchange()));
        }

        @Test
        void malformedJsonIsABodyError() throws URISyntaxException {
            var e = assertThrows(ExchangeExpressionException.class,
                    () -> eval("$.a", post("/foo").contentType(APPLICATION_JSON).body("{\"a\":").buildExchange()));
            assertTrue(e.isBodyError());
            assertEquals(400, e.problemDetails(false, "test", REQUEST).getStatus());
            assertEquals(502, e.problemDetails(false, "test", RESPONSE).getStatus());
        }

        @Test
        void textPlainThatIsNotJsonIsABodyError() throws URISyntaxException {
            var e = assertThrows(ExchangeExpressionException.class,
                    () -> eval("$.a", post("/foo").contentType(TEXT_PLAIN).body("hello").buildExchange()));
            assertTrue(e.isBodyError());
        }

        /**
         * Unlike an XPath comparison, a JSONPath filter with != matches an object that lacks the field.
         */
        @Test
        void notEqualsFilterOnEmptyBodyMatches() throws URISyntaxException {
            assertTrue(eval("$[?(@.a != 'admin')]", get("/foo").buildExchange()));
        }

        @Test
        void rootOfEmptyBodyExists() throws URISyntaxException {
            assertTrue(eval("$", get("/foo").buildExchange()));
        }

        private boolean eval(String jsonpath, Exchange exc) {
            return expression(new InterceptorAdapter(router), JSONPATH, jsonpath).evaluate(exc, REQUEST, Boolean.class);
        }
    }

    @Test
    void array() throws URISyntaxException {
        var expr = expression( new InterceptorAdapter(router), JSONPATH, "$[0]");
        assertEquals(1, expr.evaluate(post("/foo").json("[1,2,3]").buildExchange(), REQUEST, Integer.class));
    }

    @Test
    void number() throws URISyntaxException {
        var expr = expression(new InterceptorAdapter(router), JSONPATH, "$");
        assertEquals(314, expr.evaluate(post("/foo").json("314").buildExchange(), REQUEST, Integer.class));
    }

    private boolean evalBool(String jsonpath, String json) throws URISyntaxException {
        return expression(new InterceptorAdapter(router), JSONPATH, jsonpath).evaluate(post("/foo").json(json).buildExchange(), REQUEST, Boolean.class);
    }

    private <T> T evaluateWithEmptyBodyFor(Class<T> type) throws URISyntaxException {
        return expression(new InterceptorAdapter(router), JSONPATH, "$.a").evaluate(get("/foo").buildExchange(), REQUEST, type);
    }
}