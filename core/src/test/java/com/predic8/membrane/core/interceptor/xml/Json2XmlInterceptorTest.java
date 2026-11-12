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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.ConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.*;
import io.restassured.path.xml.*;
import org.junit.jupiter.api.*;
import org.xml.sax.*;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.io.*;
import java.net.*;
import java.util.Iterator;

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
        interceptor.init(new DefaultRouter());
    }

    @Test
    void normalRequest() throws Exception {
        Exchange exc = put("/person").json(mike).buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertTrue(msg.isXML());
        assertXPath("Mike", msg, "/person/name");
        assertXPath("San Francisco", msg, "/person/city");
        assertTrue(msg.getBodyAsStringDecoded().contains(UTF_8.name()));
    }

    @Test
    void normalResponse() throws Exception {
        Exchange exc = get("/foo").buildExchange();
        exc.setResponse(Response.ok().json(mike).build());
        assertEquals(CONTINUE,  interceptor.handleResponse(exc));
        Message msg = exc.getResponse();
        assertTrue(msg.isXML());
        assertXPath("Mike", msg, "/person/name");
        assertXPath("San Francisco", msg, "/person/city");
    }

    @Test
    void single() throws Exception {
        Exchange exc = put("/place").json(single).buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertTrue(msg.isXML());
        assertXPath("Berlin", msg, "/place");
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
        assertXPath("1", msg, "/root/a");
        assertXPath("2", msg, "/root/b");
    }

    @Test
    void noRootWithRootNameSpecified() throws Exception {
        interceptor.setRoot("top");
        interceptor.init();
        Exchange exc = put("/no-root").json(noRoot).buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertTrue(msg.isXML());
        assertXPath("1", msg, "/top/a");
        assertXPath("2", msg, "/top/b");
    }

    @Test
    void array() throws URISyntaxException, XPathExpressionException {
        Exchange exc = put("/array").json("[1,2,3]").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertXPath("1",msg,"/array/item[1]");
        assertXPath("2",msg,"/array/item[2]");
        assertXPath("3",msg,"/array/item[3]");
    }

    @Test
    void arrayWithRoot() throws URISyntaxException, XPathExpressionException {
        interceptor.setRoot("myRoot");
        interceptor.init();
        Exchange exc = put("/array").json("[1,2,3]").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        var msg = exc.getRequest();
        assertXPath("1",msg,"/myRoot/array/item[1]");
        assertXPath("2",msg,"/myRoot/array/item[2]");
        assertXPath("3",msg,"/myRoot/array/item[3]");
    }

    @Test
    void arrayOneElement() throws Exception {
        Exchange exc = put("/array").json("[1]").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        assertXPath("1", (Message) exc.getRequest(),"/array/item[1]");
    }

    @Test
    void number() throws Exception {
        Exchange exc = put("/number").json("1").buildExchange();
        assertEquals(CONTINUE,  interceptor.handleRequest(exc));
        Message msg = exc.getRequest();
        assertXPath("1", msg, "/root");
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

    private void assertXPath(String expected, Message msg, String xpath) throws XPathExpressionException {
        assertEquals(expected, xPath(msg.getBodyAsStringDecoded(), xpath));
    }

    private static Json2XmlInterceptor withSchema(String xsd) {
        final var interceptor = new Json2XmlInterceptor();
        interceptor.setXmlSchema(xsd);
        interceptor.init(new DefaultRouter());
        return interceptor;
    }

    @Test
    void anXmlSchemaWritesFieldsInDeclaredOrderAndNamespace() throws Exception {
        final var exc = put("/order").json("""
                {"order": {
                    "address": {"zip": "01234", "city": "Bonn"},
                    "item": [{"quantity": 2, "article": "Coffee"}, {"article": "Tea", "quantity": 1}],
                    "id": 42
                }}""").buildExchange();

        assertEquals(CONTINUE, withSchema("classpath:/xml-schema/shop.xsd").handleRequest(exc));

        final var xml = exc.getRequest().getBodyAsStringDecoded();
        assertTrue(exc.getRequest().isXML());
        assertEquals("42", shopXPath(xml, "/s:order/s:id"));
        assertEquals("2", shopXPath(xml, "count(/s:order/s:item)"), "an array becomes repeated elements: " + xml);
        assertEquals("Tea", shopXPath(xml, "/s:order/s:item[2]/s:article"));
        assertEquals("Bonn", shopXPath(xml, "/s:order/c:address/c:city"));
        assertTrue(xml.indexOf("<id") < xml.indexOf("item") && xml.indexOf("item") < xml.indexOf("address"),
                "fields in XSD order: " + xml);
    }

    @Test
    void aRootKeyTheXmlSchemaDoesNotDeclareIsA400() throws Exception {
        final var exc = put("/x").json("{\"magazine\": {}}").buildExchange();

        assertEquals(ABORT, withSchema("classpath:/xml-schema/library.xsd").handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("magazine"));
    }

    @Test
    void jsonWithoutASingleRootKeyIsA400() throws Exception {
        final var exc = put("/x").json(noRoot).buildExchange();

        assertEquals(ABORT, withSchema("classpath:/xml-schema/library.xsd").handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void jsonThatCannotBeConvertedWithAnXmlSchemaKeepsItsExplanationInProduction() throws Exception {
        final var exc = put("/x").json("{\"library\": ").buildExchange();
        final var interceptor = withSchema("classpath:/xml-schema/library.xsd");
        interceptor.getRouter().getConfiguration().setProduction(true);

        assertEquals(ABORT, interceptor.handleRequest(exc));
        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("The JSON request could not be converted to XML."), body);
    }

    @Test
    void anUndeclaredRootKeyFromTheBackendIsA500ThatHidesIt() throws Exception {
        final var exc = get("/x").buildExchange();
        exc.setResponse(Response.ok().json("{\"db-host-3\": {}}").build());

        final var interceptor = withSchema("classpath:/xml-schema/library.xsd");
        interceptor.getRouter().getConfiguration().setProduction(true);

        assertEquals(ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertFalse(exc.getResponse().getBodyAsStringDecoded().contains("db-host-3"));
    }

    @Test
    void rootCannotBeCombinedWithAnXmlSchema() {
        final var interceptor = new Json2XmlInterceptor();
        interceptor.setXmlSchema("classpath:/xml-schema/library.xsd");
        interceptor.setRoot("library");

        final var e = assertThrows(ConfigurationException.class, () -> interceptor.init(new DefaultRouter()));
        assertTrue(e.getMessage().contains("root"), e.getMessage());
    }

    @Test
    void xml2JsonAndBackWithTheSameXmlSchemaKeepsTheContent() throws Exception {
        final var original = """
                <library name="City">
                    <book id="7">
                        <title>Dune</title>
                        <author>Frank Herbert</author>
                        <author>Brian Herbert</author>
                        <pages>412</pages>
                        <price currency="EUR">12.5</price>
                        <available>true</available>
                        <note xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
                    </book>
                </library>""";
        final var xml2Json = Xml2JsonInterceptorTest.withSchema("classpath:/xml-schema/library.xsd");

        final var exc = post("/").contentType("application/xml").body(original).buildExchange();
        xml2Json.handleRequest(exc);
        final var json = exc.getRequest().getBodyAsStringDecoded();
        assertEquals(CONTINUE, withSchema("classpath:/xml-schema/library.xsd").handleRequest(exc));
        final var xml = exc.getRequest().getBodyAsStringDecoded();
        assertEquals("Brian Herbert", xPath(xml, "/library/book/author[2]"));
        assertEquals("EUR", xPath(xml, "/library/book/price/@currency"));
        xml2Json.handleRequest(exc);

        final var mapper = new ObjectMapper();
        assertEquals(mapper.readTree(json), mapper.readTree(exc.getRequest().getBodyAsStringDecoded()),
                "XML -> JSON -> XML -> JSON is stable: " + xml);
    }

    private static String shopXPath(String body, String expression) throws XPathExpressionException {
        final var xpath = xPathFactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return switch (prefix) {
                    case "s" -> "https://example.com/shop";
                    case "c" -> "https://example.com/common";
                    default -> null;
                };
            }
            public String getPrefix(String namespaceURI) { return null; }
            public Iterator<String> getPrefixes(String namespaceURI) { return null; }
        });
        final var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            final var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
            return xpath.evaluate(expression, doc);
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IOException e) {
            throw new AssertionError(e);
        }
    }
}
