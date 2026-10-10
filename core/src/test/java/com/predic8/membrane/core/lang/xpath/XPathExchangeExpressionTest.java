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

package com.predic8.membrane.core.lang.xpath;

import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;
import static org.junit.jupiter.api.Assertions.*;

class XPathExchangeExpressionTest extends AbstractExchangeExpressionTest {

    @Override
    protected Language getLanguage() {
        return XPATH;
    }

    @Override
    protected String getContentType() {
        return APPLICATION_XML;
    }

    @Override
    protected Builder getRequestBuilder() throws URISyntaxException {
        return post("/foo")
                .contentType(APPLICATION_XML)
                .body("""
                        <persons id="7">
                            <name>John Doe</name>
                            <name>James Smith</name>
                            <name>Thomas Müller</name>
                        </persons>
                        """);
    }

    @Test
    void booleanSimple() {
        assertTrue(evalBool("true()"));
        assertFalse(evalBool("false()"));
    }

    @Test
    void truth() {
        assertTrue(evalBool("//persons"));
        assertTrue(evalBool("//persons/@id"));
        assertFalse(evalBool("//unknown"));
        assertTrue(evalBool("//persons/@id = 7"));
    }

    @Test
    void attribute() {
        assertEquals("7", evalString("//persons/@id"));
    }

    @Test
    void getStringTextContent() {
        assertEquals("John Doe", evalString("/persons/name[1]"));
    }

    @Test
    void getNoExistingElement() {
        assertEquals("", evalString("//persons/wrong"));
    }

    // Object

    @Test
    void getList() {
        var o = evalObject("//persons/name");
        if (o instanceof NodeList nl) {
            assertEquals(3, nl.getLength());
            assertEquals("John Doe", nl.item(0).getTextContent());
            assertEquals("James Smith", nl.item(1).getTextContent());
            assertEquals("Thomas Müller", nl.item(2).getTextContent());
            return;
        }
        fail();
    }

    /**
     * XPath result is evaluated as a string (XPath 1.0 behavior):
     */
    @Test
    void getListString() {
        assertEquals("John Doe", evalString("//persons/name"));
    }

    @Test
    void getSingleElement() {
        var o = evalObject("//persons/name[2]");
        if (o instanceof NodeList nl) {
            assertEquals(1, nl.getLength());
            assertEquals("James Smith", nl.item(0).getTextContent());
            return;
        }
        fail();
    }

    // Other

    @Test
    void wrongContentType() {
        exchange.getRequest().getHeader().setContentType(APPLICATION_JSON);
        assertEquals("", evalString("/persons/name[1]"));
    }

    @Nested
    class Body {

        @Test
        void emptyBodyIsAnEmptyDocument() throws URISyntaxException {
            var exc = post("/foo").contentType(APPLICATION_XML).buildExchange();
            assertFalse(eval("/a", exc, Boolean.class));
            assertTrue(eval("not(/a)", exc, Boolean.class));
            assertFalse(eval("/a != 'admin'", exc, Boolean.class));
            assertTrue(eval("string(/a) != 'admin'", exc, Boolean.class));
            assertEquals("0", eval("count(//a)", exc, String.class));
            assertEquals("", eval("/a", exc, String.class));
        }

        @Test
        void foreignContentTypeIsAnEmptyDocument() throws URISyntaxException {
            var exc = post("/foo").contentType(APPLICATION_JSON).body("{\"a\":1}").buildExchange();
            assertFalse(eval("/a", exc, Boolean.class));
            assertTrue(eval("not(/a)", exc, Boolean.class));
        }

        @Test
        void textPlainIsParsed() throws URISyntaxException {
            assertTrue(eval("/a", post("/foo").contentType(TEXT_PLAIN).body("<a/>").buildExchange(), Boolean.class));
        }

        @Test
        void missingContentTypeIsParsed() throws URISyntaxException {
            assertTrue(eval("/a", post("/foo").body("<a/>").buildExchange(), Boolean.class));
        }

        @Test
        void formDataWithXmlIsParsed() throws URISyntaxException {
            assertTrue(eval("/a", post("/foo").contentType(APPLICATION_X_WWW_FORM_URLENCODED).body("<a/>").buildExchange(), Boolean.class));
        }

        @Test
        void formDataThatIsNotXmlIsAnEmptyDocument() throws URISyntaxException {
            var exc = post("/foo").contentType(APPLICATION_X_WWW_FORM_URLENCODED).body("a=1&b=2").buildExchange();
            assertFalse(eval("/a", exc, Boolean.class));
            assertTrue(eval("not(/a)", exc, Boolean.class));
        }

        @Test
        void malformedXmlIsABodyError() throws URISyntaxException {
            var e = assertThrows(ExchangeExpressionException.class,
                    () -> eval("/a", post("/foo").contentType(APPLICATION_XML).body("<a><b></a>").buildExchange(), Boolean.class));
            assertTrue(e.isBodyError());
            assertTrue(e.getMessage().contains("must be terminated"), e.getMessage());
        }

        @Test
        void textPlainThatIsNotXmlIsABodyError() throws URISyntaxException {
            var e = assertThrows(ExchangeExpressionException.class,
                    () -> eval("/a", post("/foo").contentType(TEXT_PLAIN).body("hello").buildExchange(), Boolean.class));
            assertTrue(e.isBodyError());
        }

        @Test
        void bodyErrorIs400InRequestAnd502InResponse() throws URISyntaxException {
            var e = assertThrows(ExchangeExpressionException.class,
                    () -> eval("/a", post("/foo").contentType(APPLICATION_XML).body("<a><b></a>").buildExchange(), Boolean.class));
            assertEquals(400, e.problemDetails(false, "test", REQUEST).getStatus());
            assertEquals(502, e.problemDetails(false, "test", RESPONSE).getStatus());
        }

        @Test
        void expressionErrorIsNoBodyError() throws URISyntaxException {
            var e = assertThrows(ExchangeExpressionException.class,
                    () -> eval("foobar][", post("/foo").contentType(APPLICATION_XML).body("<a/>").buildExchange(), Boolean.class));
            assertFalse(e.isBodyError());
            assertEquals(500, e.problemDetails(false, "test", REQUEST).getStatus());
        }

        private <T> T eval(String xpath, Exchange exc, Class<T> type) {
            return expression(new InterceptorAdapter(router), XPATH, xpath).evaluate(exc, REQUEST, type);
        }
    }

    @Nested
    class Namespaces {

        Exchange pExc;

        @BeforeEach
        void setup() throws URISyntaxException {
            pExc = post("/person").xml("""
                    <p8:person xmlns:p8="https://predic8.de">
                        <p8:firstname>Trevor</p8:firstname>
                    </p8:person>
                    """).buildExchange();
        }

        @Test
        void localName() {
            assertEquals("Trevor", expression(new InterceptorAdapter(router), getLanguage(),
                    "//*[local-name()='firstname']")
                    .evaluate(pExc, REQUEST, String.class));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void contentNoAllowedInProlog() throws URISyntaxException {
            try {
                expression(null, XPATH, "/foo").evaluate(post("/person").xml("""
                        A<foo/>""").buildExchange(), REQUEST, String.class);
                fail();
            } catch (ExchangeExpressionException e) {
                assertFalse(e.getMessage().contains("XmlParseException"));
                assertTrue(e.getMessage().contains("prolog"));
                var pd = getProblemDetails(e);
                assertEquals("A<foo/>", pd.getInternal().get("body").toString());
            }
        }

        @Test
        void trailingCharacters() throws URISyntaxException {
            try {
                expression(null, XPATH, "/foo").evaluate(post("/person").xml("""
                        <foo/>A""").buildExchange(), REQUEST, String.class);
                fail();
            } catch (ExchangeExpressionException e) {
                assertFalse(e.getMessage().contains("XmlParseException"));
                assertTrue(e.getMessage().contains("trailing"));
                var pd = getProblemDetails(e);
                assertEquals("<foo/>A", pd.getInternal().get("body").toString());
            }
        }

        private static @NotNull ProblemDetails getProblemDetails(ExchangeExpressionException e) {
            var pd = user(false, "test");
            e.provideDetails(pd);
            return pd;
        }
    }
}