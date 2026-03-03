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

package com.predic8.membrane.core.interceptor.lang;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A simple test is enough to test the logic of the interceptor.
 * Complex expressions are tested in the ExchangeExpressionTest, and ...
 */
class SetBodyInterceptorTest {

    private static final ObjectMapper om = new ObjectMapper();

    private SetBodyInterceptor sbi;
    private Exchange exc;

    @BeforeEach
    void setup() throws URISyntaxException {
        sbi = new SetBodyInterceptor();
        exc = get("/foo").buildExchange();
        exc.setResponse(notImplemented().body("bar").build());
    }

    @Test
    void nullResult() {
        sbi.setValue("null");
        sbi.init(new DefaultRouter());
        sbi.handleRequest(exc);
        assertEquals("null", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void evalOfSimpleExpression() {
        sbi.setValue("${path}");
        sbi.init(new DefaultRouter());
        sbi.handleRequest(exc);
        assertEquals("/foo", exc.getRequest().getBodyAsStringDecoded());
    }

    /**
     * When inserting a value from JSONPath into a JSON document like:
     * { "a": ${.a} }
     * and the value is null, the document should be:
     * { "a": null }
     */
    @Nested
    class Null {

        @Test
        void escapeNullJsonPath() throws URISyntaxException {
            callSetBody(JSONPATH, "${$.a}");
        }

        @Test
        void escapeNullGroovy() throws URISyntaxException {
            callSetBody(GROOVY, "${fn.jsonPath('$.a')}");
        }

        private void callSetBody(ExchangeExpression.Language language, String expression) throws URISyntaxException {
            exc = setJsonSample();
            sbi.setLanguage(language);
            sbi.setContentType(APPLICATION_JSON_UTF8);
            sbi.setValue(expression);
            sbi.init(new DefaultRouter());
            sbi.handleRequest(exc);
            assertEquals("null", exc.getRequest().getBodyAsStringDecoded());
        }

        private Exchange setJsonSample() throws URISyntaxException {
            return post("/foo").json("""
                    {"a":null}
                    """).buildExchange();
        }
    }

    @Nested
    class escaping {

        @Test
        void escape() throws URISyntaxException, JsonProcessingException {

            exc = setJsonSample();
            sbi.setLanguage(SPEL);
            sbi.setContentType(APPLICATION_JSON_UTF8);
            sbi.setValue("""
                    {
                        "string": ${'Dublin'},
                        "number": ${123},
                        "boolean": ${true},
                        "null": ${null},
                        "array": ${new String[]{'a','b','c'}},
                        "map": ${ {'a':'b', 'c':'d'} }
                    }
                    """);
            sbi.init(new DefaultRouter());
            sbi.handleRequest(exc);

            var json = om.readTree(exc.getRequest().getBodyAsStringDecoded());
            assertEquals("Dublin", json.get("string").asText());
            assertEquals(123, json.get("number").asInt());
            assertEquals(true, json.get("boolean").asBoolean());
            assertEquals(3, json.get("array").size());
            assertEquals("a", json.get("array").get(0).asText());
            assertEquals("b", json.get("array").get(1).asText());
            assertEquals("c", json.get("array").get(2).asText());
            assertEquals(2, json.get("map").size());
            assertEquals("b", json.get("map").get("a").asText());
            assertEquals("d", json.get("map").get("c").asText());
            assertTrue(json.get("null").isNull());
        }

        @Test
        void escape_xpath() throws Exception {
            exc = post("/foo").json("""
                    <root>
                      <string>Dublin</string>
                      <number>123</number>
                      <boolean>true</boolean>
                      <array>
                        <item>a</item>
                        <item>b</item>
                        <item>c</item>
                      </array>
                    </root>
                    """).buildExchange();

            sbi.setLanguage(XPATH);
            sbi.setContentType(APPLICATION_JSON_UTF8);
            sbi.setValue("""
                    {
                      "string": ${/root/string/text()},
                      "number": ${number(/root/number)},
                      "boolean": ${/root/boolean/text() = 'true'},
                      "null": null,
                      "array": ${/root/array/item/text()}
                    }
                    """);

            sbi.init(new DefaultRouter());
            sbi.handleRequest(exc);

            var body = exc.getRequest().getBodyAsStringDecoded();
            var json = om.readTree(body);
            assertEquals("Dublin", json.get("string").asText());
            assertEquals(123, json.get("number").asInt());
            assertTrue(json.get("boolean").asBoolean());

            assertEquals(3, json.get("array").size());
            assertEquals("a", json.get("array").get(0).asText());
            assertEquals("b", json.get("array").get(1).asText());
            assertEquals("c", json.get("array").get(2).asText());

            assertTrue(json.get("null").isNull());
        }

        private Exchange setJsonSample() throws URISyntaxException {
            return post("/foo").json("""
                    {"a":null}
                    """).buildExchange();
        }

    }

    @Test
    void response() {
        sbi.setValue("SC: ${statusCode}");
        sbi.init(new DefaultRouter());
        sbi.handleResponse(exc);
        assertEquals("SC: 501", exc.getResponse().getBodyAsStringDecoded());
    }
}