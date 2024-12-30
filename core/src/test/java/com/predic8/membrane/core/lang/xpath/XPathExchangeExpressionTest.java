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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static org.junit.jupiter.api.Assertions.*;

class XPathExchangeExpressionTest {

    Exchange exc;

    @BeforeEach
    void setup() throws URISyntaxException {
        exc = Request.post("/foo")
                .contentType(APPLICATION_JSON)
                .body("""
                <person id="7">
                    <name>John Doe</name>
                </person>
                """).buildExchange();
    }

    @Test
    void simple() {
        booleanAssertTrue("true()");
    }

    @Test
    void attribute() {
        booleanAssertTrue("//person/@id = 7");
    }

    @Test
    void getStringAttribute() {
        assertGetString("7","/person/@id");
    }

    @Test
    void getStringTextContent() {
        assertGetString("John Doe","/person/name");
    }

    @Test
    void getNoExistingElement() {
        assertGetString("","/person/wrong");
    }

    @Test
    void wrongContent() {
        exc.getRequest().setBodyContent("{}".getBytes());
        assertThrows(Exception.class, () -> assertGetString("John Doe","/person/name"));
    }

    @Test
    void wrongContentType() {
        exc.getRequest().getHeader().setContentType(APPLICATION_JSON);
        assertGetString("John Doe","/person/name");
    }

    protected void booleanAssertTrue(String xpath) {
        assertTrue(new XPathExchangeExpression(xpath).evaluate(exc, REQUEST, Boolean.class));
    }

    protected void assertGetString(String expected, String xpath) {
        assertEquals(expected, new XPathExchangeExpression(xpath).evaluate(exc, REQUEST, String.class));
    }
}