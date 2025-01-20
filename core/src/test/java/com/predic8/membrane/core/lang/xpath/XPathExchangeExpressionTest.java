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

import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

class XPathExchangeExpressionTest extends AbstractExchangeExpressionTest {

    @Override
    protected Language getLanguage() {
        return XPATH;
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

    // Boolean

    @Test
    void boolSimple() {
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

    // String

    @Test
    void attribute() {
        assertEquals("7",evalString("//persons/@id"));
    }

    @Test
    void getStringTextContent() {
        assertEquals("John Doe",evalString("/persons/name[1]"));
    }

    @Test
    void getNoExistingElement() {
        assertEquals("", evalString("//persons/wrong"));
    }

    // Object

    @Test
    void getList() {
        Object o = evalObject("//persons/name");
        if (o instanceof NodeList nl) {
            assertEquals(3, nl.getLength());
            return;
        }
        fail();
    }

    @Test
    void getSingleElement() {
        Object o = evalObject("//persons/name[2]");
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
        assertEquals("John Doe",evalString("/persons/name[1]"));
    }

}