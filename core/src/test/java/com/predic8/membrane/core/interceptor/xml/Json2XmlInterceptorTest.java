/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xml;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;
import org.xml.sax.*;

import javax.xml.xpath.*;
import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;


public class Json2XmlInterceptorTest {

    private static final XPathFactory xPathFactory = XPathFactory.newInstance();

    Json2XmlInterceptor interceptor;

    static final String mike = """
                {
                    "person": {
                        "name": "Mike",
                        "city": "San Francisco"
                    }
                }
                """;

    static final String single = """
            {
                "place": "Berlin"
            }
            """;

    static final String noRoot = """
            {
                "a": 1,
                "b": 2
            }
            """;

    @BeforeEach
    void setUp() {
        interceptor = new Json2XmlInterceptor();
        interceptor.init(new Router());
    }

    @Test
    void normalRequest() throws Exception {
        Exchange exc = put("/person").json(mike).buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertTrue(msg.isXML());
        assertEquals("Mike", xPath(msg.getBodyAsStringDecoded(), "/person/name"));
        assertEquals("San Francisco", xPath(msg.getBodyAsStringDecoded(), "/person/city"));
        assertTrue(msg.getBodyAsStringDecoded().contains(UTF_8.name()));
    }

    @Test
    void normalResponse() throws Exception {
        Exchange exc = get("/foo").buildExchange();
        exc.setResponse(Response.ok().json(mike).build());
        assertEquals(CONTINUE,  interceptor.handleResponse(exc));
        Message msg = exc.getResponse();
        assertTrue(msg.isXML());
        assertEquals("Mike", xPath(msg.getBodyAsStringDecoded(), "/person/name"));
        assertEquals("San Francisco", xPath(msg.getBodyAsStringDecoded(), "/person/city"));
    }

    @Test
    void single() throws Exception {
        Exchange exc = put("/place").json(single).buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertTrue(msg.isXML());
        assertEquals("Berlin", xPath(msg.getBodyAsStringDecoded(), "/place"));
    }

    @Test
    void noRoot() throws Exception {
        Exchange exc = put("/no-root").json(noRoot).buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertTrue(msg.isXML());
        assertEquals("1", xPath(msg.getBodyAsStringDecoded(), "/root/a"));
        assertEquals("2", xPath(msg.getBodyAsStringDecoded(), "/root/b"));
    }

    @Test
    void noRootWithRootNameSpecified() throws Exception {
        interceptor.setRoot("top");
        Exchange exc = put("/no-root").json(noRoot).buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertTrue(msg.isXML());
        assertEquals("1", xPath(msg.getBodyAsStringDecoded(), "/top/a"));
        assertEquals("2", xPath(msg.getBodyAsStringDecoded(), "/top/b"));
    }

    @Test
    void invalidJSON() throws URISyntaxException {
        Exchange exc = put("/invalid").json("{ invalid").buildExchange();
        assertEquals(ABORT,  interceptor.handleRequest(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Error parsing JSON"));
    }

    private static String xPath(String body, String expression) throws XPathExpressionException {
        System.out.println("body = " + body);
        return xPathFactory.newXPath().evaluate(expression, new InputSource(new StringReader(body)));
    }

}
