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

package com.predic8.membrane.core.interceptor.templating;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.security.BasicHttpSecurityScheme;
import com.predic8.membrane.core.util.*;
import org.json.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static java.lang.Boolean.*;
import static java.lang.System.*;
import static java.nio.charset.StandardCharsets.*;
import static javax.xml.xpath.XPathConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TemplateInterceptorTest {

    private final ObjectMapper om = new ObjectMapper();

    TemplateInterceptor ti;
    Exchange exc = new Exchange(null);
    Request req;
    static Router router;
    static ResolverMap map;

    @BeforeAll
    static void setupFiles() {
        router = mock(Router.class);
        map = new ResolverMap();
        when(router.getResolverMap()).thenReturn(map);
        when(router.getUriFactory()).thenReturn(new URIFactory());
    }

    @BeforeEach
    void setUp(){
        ti = new TemplateInterceptor();
        exc = new Exchange(null);
        req = new Request.Builder().build();
        exc.setRequest(req);

        exc.setProperty("title", "minister");
        List<String> lst = new ArrayList<>();
        lst.add("food1");
        lst.add("food2");
        exc.setProperty("items", lst);
    }

    @Test
    void accessJson() throws Exception {
        Exchange exchange = post("/cities").contentType(APPLICATION_JSON).body("""
                { "city": "Da Nang" }
                """).buildExchange();

        // Also test save nav with ?.
        invokeInterceptor(exchange, """
                City: <%= json.city %>
                """, TEXT_PLAIN);

        var r = exchange.getRequest();
        assertTrue(r.getBodyAsStringDecoded().contains("City: Da Nang"));
        assertEquals(TEXT_PLAIN, r.getHeader().getContentType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void createJson() throws Exception {
        Exchange exchange = Request.put("/foo").contentType(APPLICATION_JSON).buildExchange();

        invokeInterceptor(exchange, """
                {"foo":7,"bar":"baz"}
                """, APPLICATION_JSON);

        assertEquals(APPLICATION_JSON, exchange.getRequest().getHeader().getContentType());

        Map<String,Object> m = om.readValue(exchange.getRequest().getBodyAsStringDecoded(),Map.class);
        assertEquals(7,m.get("foo"));
        assertEquals("baz",m.get("bar"));
    }

    @Test
    void accessBindings() throws Exception {
        Exchange exchange = post("/foo?a=1&b=2").contentType(TEXT_PLAIN).body("vlinder").buildExchange();
        exchange.setProperty("baz",7);

        invokeInterceptor(exchange, """
                <% for(h in header.allHeaderFields) { %>
                   <%= h.headerName %> : <%= h.value %>
                <% } %>
                Exchange: <%= exc %>
                Flow: <%= flow %>
                Message.version: <%= message.version %>
                Body: <%= message.body %>
                Properties: <%= property.baz %>
                <% for(p in property) { %>
                   Key: <%= p.key %> : <%= p.value %>
                <% } %>
                New: <%= property.baz %>
                Query Params:
                A: <%= params.a %>
                B: <%= params.b %>
                
                <% for(p in params) { %>
                    <%= p.key %> : <%= p.value %>
                <% } %>
                """, APPLICATION_JSON);
        
        String body = exchange.getRequest().getBodyAsStringDecoded();
        assertTrue(body.contains("/foo"));
        assertTrue(body.contains("Flow: REQUEST"));
        assertTrue(body.contains("Body: vlinder"));
        assertTrue(body.contains("New: 7"));
        assertTrue(body.contains("A: 1"));
        assertTrue(body.contains("B: 2"));
    }

    @Test
    void xmlFromFileTest() throws Exception {
        setAndHandleRequest("xml/project_template.xml");
        assertEquals("minister", evaluateXPathAndReturnFirstNode(createXPathExpression("/project/part[2]/title")).trim());
    }

    private static XPathExpression createXPathExpression(String getTitlePath) throws XPathExpressionException {
        return XPathFactory.newInstance().newXPath().compile(getTitlePath);
    }

    private String evaluateXPathAndReturnFirstNode(XPathExpression xpath) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        return ((NodeList) xpath.evaluate(DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(exc.getRequest().getBodyAsStream()), NODESET)).item(0).getFirstChild().getNodeValue();
    }

    @Test
    void nonXmlTemplateListTest() {
        setAndHandleRequest("json/template_test.json");

        assertEquals("food1",
                new JSONObject(exc.getRequest().getBodyAsStringDecoded()).getJSONArray("orders")
                        .getJSONObject(0).getJSONArray("items").getString(0));

        assertEquals("minister",
                new JSONObject(exc.getRequest().getBodyAsStringDecoded()).getJSONObject("meta").getString("title"));
    }

    @Test
    void initTest() {
        assertThrows(ConfigurationException.class, () -> {
            ti.setLocation("./template_test.json");
            ti.setSrc("${minister}");
            ti.init(router);
        });
    }

    @Test
    void notFoundTemplateException() {
        assertThrows(ConfigurationException.class, () -> {
            ti.setLocation("./not_existent_file");
            ti.init(router);
        });
    }

    @Test
    void innerTagTest() {
        ti.setSrc("${property.title}");
        ti.init(router);
        ti.handleRequest(exc);
        assertEquals("minister", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void contentTypeTestXml() {
        setAndHandleRequest("xml/project_template.xml");
        assertTrue(exc.getRequest().isXML());
    }

    @Test
    void contentTypeTestOther() {
        ti.setContentType(APPLICATION_JSON);
        setAndHandleRequest("json/template_test.json");
        assertTrue(exc.getRequest().isJSON());
    }

    @Test
    void contentTypeTestJson() {
        setAndHandleRequest("json/template_test.json");
        assertEquals(APPLICATION_JSON,exc.getRequest().getHeader().getContentType());
    }

    @Test
    void contentTypeTestNoXml() {
        ti.setSrc("normal text");
        ti.init(router);
        ti.handleRequest(exc);
        assertEquals(TEXT_PLAIN,exc.getRequest().getHeader().getContentType());
    }

    @Test
    void testPrettify() {
        String inputJson = "\t{\n\n\t\t\"name\":\"John\"\t\t,\"age\":30}";

        String expectedPrettyJson = "{"
                                    + lineSeparator() + "  \"name\" : \"John\","
                                    + lineSeparator() + "  \"age\" : 30"
                                    + lineSeparator() + "}";

        ti.setContentType(APPLICATION_JSON);
        ti.setSrc(inputJson);
        ti.setPretty( TRUE);
        ti.init();
        assertArrayEquals(expectedPrettyJson.getBytes(UTF_8), ti.prettify(inputJson.getBytes(UTF_8)));
    }

    @Test
    void prettifyWithInvalidJson() {
        String invalid = "{name:\"John,age:30}";
        ti.setContentType(APPLICATION_JSON);
        ti.setSrc(invalid);
        ti.setPretty(TRUE);
        ti.init(router);
        ti.handleRequest(exc);

        // Because JSON is invalid it should not change anything
        assertEquals(invalid, exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void builtInFunctions() {
        exc.setProperty(SECURITY_SCHEMES, List.of(new BasicHttpSecurityScheme().username("alice")));
        ti.setContentType(APPLICATION_JSON);
        ti.setSrc("""
        { "foo": "${fn.user()}" }
        """);
        ti.init(router);
        ti.handleRequest(exc);

        assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("\"foo\": \"alice\""));
    }

    private void setAndHandleRequest(String location) {
        ti.setLocation(Paths.get("src/test/resources/" + location).toString());
        ti.init(router);
        ti.handleRequest(exc);
    }


    private static void invokeInterceptor(Exchange exchange, String template, String mimeType) {
        TemplateInterceptor interceptor = new TemplateInterceptor();
        interceptor.setSrc(template);
        interceptor.setContentType(mimeType);
        interceptor.init(router);
        interceptor.handleRequest(exchange);
    }
}