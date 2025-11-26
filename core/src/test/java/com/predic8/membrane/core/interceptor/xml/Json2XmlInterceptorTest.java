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
import io.restassured.path.json.*;
import io.restassured.path.xml.*;
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
    void nested() throws URISyntaxException {
        Exchange exc = put("/nested").json("""
            {
                "one": [1],
                "nested": [[2]],
                "three": [[[3]]]
            }
            """).buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        String xml = exc.getRequest().getBodyAsStringDecoded();
        XmlPath xp = new XmlPath(xml);

        // one = [1]
        assertEquals(1, xp.getInt("root.one.array.item"));
        assertEquals(1, xp.getList("root.one.array.item").size());

        // nested = [[2]]
        assertEquals(2, xp.getInt("root.nested.array.item.array.item"));
        assertEquals(1, xp.getList("root.nested.array.item.array.item").size());

        // three = [[[3]]]
        assertEquals(3, xp.getInt("root.three.array.item.array.item.array.item"));
        assertEquals(1, xp.getList("root.three.array.item.array.item.array.item").size());
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

    @Test
    void array() throws URISyntaxException, XPathExpressionException {
        Exchange exc = put("/array").json("[1,2,3]").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertEquals("1", xPath(msg.getBodyAsStringDecoded(), "/array/item[1]"));
        assertEquals("2", xPath(msg.getBodyAsStringDecoded(), "/array/item[2]"));
        assertEquals("3", xPath(msg.getBodyAsStringDecoded(), "/array/item[3]"));
    }

    @Test
    void arrayWithRoot() throws URISyntaxException, XPathExpressionException {
        interceptor.setRoot("myRoot");
        Exchange exc = put("/array").json("[1,2,3]").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertEquals("1", xPath(msg.getBodyAsStringDecoded(), "/myRoot/array/item[1]"));
        assertEquals("2", xPath(msg.getBodyAsStringDecoded(), "/myRoot/array/item[2]"));
        assertEquals("3", xPath(msg.getBodyAsStringDecoded(), "/myRoot/array/item[3]"));
    }

    @Test
    void arrayOneElement() throws Exception {
        Exchange exc = put("/array").json("[1]").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertEquals("1", xPath(msg.getBodyAsStringDecoded(), "/array/item[1]"));
    }

    @Test
    void number() throws Exception {
        Exchange exc = put("/number").json("1").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        System.out.println(msg.getBodyAsStringDecoded());
        assertEquals("1", xPath(msg.getBodyAsStringDecoded(), "/root"));
    }

    @Test
    void invalid() throws URISyntaxException {
        Exchange exc = put("/invalid").json("{").buildExchange();
        assertEquals(ABORT,  interceptor.handleRequest(exc));
        Response res = exc.getResponse();
        assertEquals(500, res.getStatusCode());
        assertEquals("Error parsing JSON",     new JsonPath(res.getBodyAsStringDecoded()).get("title"));
    }

    private static String xPath(String body, String expression) throws XPathExpressionException {
        return xPathFactory.newXPath().evaluate(expression, new InputSource(new StringReader(body)));
    }
}
