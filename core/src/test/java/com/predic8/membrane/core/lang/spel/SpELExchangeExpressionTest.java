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
package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("rawtypes")
class SpELExchangeExpressionTest extends AbstractExchangeExpressionTest {

    @Override
    protected Request.Builder getRequestBuilder() throws URISyntaxException {
        return post("/foo/7/9?city=Paris")
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
                    }
                }
                """);
    }

    @Override
    protected Language getLanguage() {
        return SPEL;
    }

    // Boolean

    @Test
    void constants() {
        assertTrue(evalBool("true"));
        assertFalse(evalBool("false"));
    }

    @Test
    void booleanNull() {
        assertFalse(evalBool("null"));
    }

    @Test
    void empty() {
        assertThrows(ConfigurationException.class,() -> evalBool(""));
    }

    @Test
    void zero() {
        assertFalse(evalBool("0"));
    }

    @Test
    void unknown() {
        assertThrows(ExchangeExpressionException.class,() -> evalBool("unknown"));
    }

    @Test
    void truth() {
        assertTrue(evalBool("property.wet"));
        assertFalse(evalBool("property['can-fly']"));
    }

    @Test
    void callBuiltInFunction() {
        assertInstanceOf(Boolean.class, evalBool("weight(3.14)"));
    }

    // String

    @Test
    void anyString() {
        assertEquals("abc", evalString("'abc'"));
    }

    @Test
    void callSpELMethod() {
        assertEquals("c", evalString("'abc'.substring(2, 3)"));
    }

    @Test
    void header() {
        assertEquals("Jelly Fish", evalString("header.name"));
    }

    @Test
    void headerBracket() {
        assertEquals("Jelly Fish", evalString("header['name']"));
    }

    @Test
    void headerEquals() {
        assertTrue(evalBool("header.foo == '42'"));
    }

    @Test
    void accessNonExistingHeader() {
        assertEquals("",evalString("header.unknown"));
    }

    @Test
    void accessNonExistingPropertyAsString() {
        assertEquals("",evalString("property.unknown"));
    }

    // Object

    @Test
    void accessNonExistingPropertyAsObject() {
        assertNull(evalObject("property.unknown"));
    }

    @Test
    void list() {
        Object o = evalObject("property.tags");
        if (!(o instanceof List l)) {
            fail();
            return;
        }
        assertEquals(2, l.size());
        assertEquals("animal", l.get(0));
        assertEquals("water", l.get(1));
    }

    @Test
    void map() {
        Object o = evalObject("property.world");
        if (!(o instanceof Map m)) {
            fail();
            return;
        }
        assertEquals("US",m.get("country"));
        assertEquals("Europe",m.get("continent"));
    }

    // Exchange Objects
    @Test
    void headerDash() {
        assertEquals("Tokio", evalString("header['x-city']"));
    }


    @Test
    void param() {
        assertEquals("Paris", evalString("param.city"));
    }

    @Test
    void pathParameters() {
        assertEquals("7", evalString("pathParam['fid']"));
        assertEquals("9", evalString("pathParam['gid']"));
    }
}