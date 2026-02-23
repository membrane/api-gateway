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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.security.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.lang.CommonBuiltInFunctions.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static java.util.List.*;
import static javax.xml.xpath.XPathConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class CommonBuiltInFunctionsTest {

    private static final ObjectMapper om = new ObjectMapper();

    static Exchange exc;

    @BeforeAll
    static void init() throws URISyntaxException {
        exc = get("foo").buildExchange();
        exc.setProperty(SECURITY_SCHEMES, List.of(
                new ApiKeySecurityScheme(HEADER, "X-Api-Key").scopes("demo", "test"),
                new BasicHttpSecurityScheme().scopes("foo", "bar")
        ));
        exc.getRequest().setBodyContent("""
                {"name":"John"}""".getBytes());
    }

    @Test
    void jsonPathReturnsValueOrNull() {
        assertEquals("John", jsonPath("$.name", exc.getRequest()));
        assertNull(jsonPath("$.foo", exc.getRequest()));
    }

    @Test
    void xpath_string() throws URISyntaxException {
        assertEquals("Fritz", CommonBuiltInFunctions.xpath("string(/person/@name)",
                post("/foo").xml("<person name='Fritz'/>").build(), null));
    }

    @Test
    void xpath_nodelist() throws URISyntaxException {
        var obj = CommonBuiltInFunctions.xpath("/person/@name",
                post("/foo").xml("<person name='Fritz'/>").build(), null);
        if (obj instanceof NodeList nl) {
            assertEquals(1, nl.getLength());
            assertEquals("Fritz", nl.item(0).getTextContent());
        } else {
            fail();
        }
    }

    @Test
    void guessReturnTypeRecognizesCommonXPathPrefixes() {
        assertSame(STRING, guessReturnType("string(//name)"));
        assertSame(STRING, guessReturnType("normalize-space(//name)"));
        assertSame(NUMBER, guessReturnType("count(//item)"));
        assertSame(NUMBER, guessReturnType("number(//price)"));
        assertSame(BOOLEAN, guessReturnType("boolean(//item)"));
        assertSame(NODE, guessReturnType("./name"));
        assertSame(NODESET, guessReturnType("//fruit"));
    }

    @Test
    void xpathEvaluatesAgainstContextWithReturnTypeInference() throws Exception {
        Document document = parseXml("""
                <root>
                  <fruit><name>Apricot</name></fruit>
                  <fruit><name>Date</name></fruit>
                </root>
                """);

        Object nodes = xpath("//fruit", document, null);
        assertInstanceOf(NodeList.class, nodes);
        assertEquals(2, ((NodeList) nodes).getLength());

        var firstFruit = ((NodeList) nodes).item(0);
        Object nameNode = xpath("./name", firstFruit, null);
        assertInstanceOf(Node.class, nameNode);
        assertEquals("name", ((Node) nameNode).getNodeName());

        assertEquals("Apricot", xpath("string(./name)", firstFruit, null));
        assertEquals(2.0, xpath("count(//fruit)", document, null));
        assertEquals(true, xpath("boolean(//fruit)", document, null));
    }

    @Test
    void xpathResolvesNamespacesFromXmlConfig() throws Exception {
        Document document = parseXml("""
                <f:root xmlns:f="https://predic8.de/fruits">
                  <f:fruit><f:name>Apricot</f:name></f:fruit>
                </f:root>
                """);

        XmlConfig cfg = new XmlConfig();
        Namespaces namespaces = new Namespaces();
        Namespaces.Namespace ns = new Namespaces.Namespace();
        ns.setPrefix("f");
        ns.setUri("https://predic8.de/fruits");
        namespaces.setNamespaces(List.of(ns));
        cfg.setNamespaces(namespaces);

        assertEquals("Apricot", xpath("string(//f:fruit/f:name)", document, cfg));
    }

    private static Document parseXml(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Test
    void hasScopeSingle() {
        assertTrue(hasScope("test", exc));
    }

    @Test
    void hasScopeAnyFromList() {
        assertTrue(hasScope(of("demo", "test"), exc));
        assertFalse(hasScope(of("quux"), exc));
    }

    @Test
    void hasScopeNoSchemes() throws URISyntaxException {
        var exc2 = get("foo").buildExchange();
        assertFalse(hasScope(exc2));
        assertFalse(hasScope(of("foo"), exc2));
    }

    @Test
    void scopesAll() {
        assertEquals(List.of("test", "demo", "bar", "foo"), scopes(exc));
    }

    @Test
    void scopesBySchemeType() {
        assertEquals(List.of("bar", "foo"), scopes("http", exc));
    }

    @Test
    void isBearerAuthorizationVariants() throws URISyntaxException {
        assertTrue(isBearerAuthorization(get("foo")
                .header(AUTHORIZATION, "Bearer 8w458934pj5u9843")
                .buildExchange()));

        assertFalse(isBearerAuthorization(get("foo")
                .header(AUTHORIZATION, "Other 8w458934pj5u9843")
                .buildExchange()));

        assertFalse(isBearerAuthorization(get("foo").buildExchange()));
    }

    @Test
    void encode() {
        assertEquals("YWxpc2U6Zmxvd2VyMjU=", base64Encode("alise:flower25"));
    }

    @Test
    void weightProducesRoughRate() {
        assertEquals(0.01, calculateRate(1), 0.05);
        assertEquals(0.5, calculateRate(50), 0.05);
        assertEquals(0.001, calculateRate(0.1), 0.005);
    }

    private double calculateRate(double weightInPercent) {
        int executedCount = 0;
        for (int i = 0; i < 1_000_000; i++) {
            if (weight(weightInPercent)) {
                executedCount++;
            }
        }
        return ((double) executedCount / 1_000_000);
    }

    @Nested
    class toJSON {

        @Test
        void map() throws Exception {
            var str = toJSON(Map.of("foo", 1, "bar", 2));
            var obj = om.readValue(str, Map.class);
            assertEquals(2, obj.size());
            assertEquals(1, obj.get("foo"));
            assertEquals(2, obj.get("bar"));
        }

        @Test
        void list() throws Exception {
            var str = toJSON(List.of("foo", 1, "bar"));
            var obj = om.readValue(str, List.class);
            assertEquals(3, obj.size());
            assertEquals("foo", obj.get(0));
            assertEquals(1, obj.get(1));
            assertEquals("bar", obj.get(2));
        }

        @Test
        void string() {
            assertEquals("\"foo\"", toJSON("foo"));
        }

        @Test
        void integer() {
            assertEquals("1", toJSON(1));
        }

        @Test
        void bool() {
            assertEquals("true", toJSON(true));
        }
    }
}
