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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.security.BasicHttpSecurityScheme;
import com.predic8.membrane.core.util.ConfigurationException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static java.lang.Boolean.TRUE;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.xml.xpath.XPathConstants.NODESET;
import static org.junit.jupiter.api.Assertions.*;

public class TemplateInterceptorTest {

    private final ObjectMapper om = new ObjectMapper();

    TemplateInterceptor ti;
    Exchange exc = new Exchange(null);
    Request req;
    Router router;

    @BeforeEach
    void setUp() {
        router = new DummyTestRouter();
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
        var exchange = Request.put("/foo").contentType(APPLICATION_JSON).buildExchange();

        invokeInterceptor(exchange, """
                {"foo":7,"bar":"baz"}
                """, APPLICATION_JSON);

        assertEquals(APPLICATION_JSON, exchange.getRequest().getHeader().getContentType());

        Map<String, Object> m = om.readValue(exchange.getRequest().getBodyAsStringDecoded(), Map.class);
        assertEquals(7, m.get("foo"));
        assertEquals("baz", m.get("bar"));
    }

    @Test
    void accessBindings() throws Exception {
        Exchange exchange = post("/foo?a=1&b=2").contentType(TEXT_PLAIN).body("vlinder").buildExchange();
        exchange.setProperty("baz", 7);

        invokeInterceptor(exchange, """
                <% for(h in header.allHeaderFields) { %>
                   <%= h.headerName %> : <%= h.value %>
                <% } %>
                Exchange: <% out<<exc %>
                Flow: <%= flow %>
                Message.version: <%= message.version %>
                Body: <% out<<message.body %>
                Properties: <%= property.baz %>
                <% for(p in property) { %>
                   Key: <%= p.key %> : <%= p.value %>
                <% } %>
                New: <%= property.baz %>
                Query Params:
                A: <%= params.a[0] %>
                B: <%= params.b[0] %>
                
                <% for(p in params) { %>
                    <%= p.key %> : <%= p.value %>
                <% } %>
                """, APPLICATION_JSON);

        String body = exchange.getRequest().getBodyAsStringDecoded();
        assertTrue(body.contains("/foo"));
        assertTrue(body.contains("Flow: \"REQUEST\""));
        assertTrue(body.contains("Body: vlinder"));
        assertTrue(body.contains("New: 7"));
        assertTrue(body.contains("A: \"1\""));
        assertTrue(body.contains("B: \"2\""));
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
    void templateExecutionErrorSee() throws Exception {
        ti.setSrc("${1/0}");
        ti.init(router);
        assertEquals(ABORT, ti.handleRequest(exc));
        assertEquals("https://membrane-api.io/problems/internal/template/execution",
                om.readTree(exc.getResponse().getBodyAsStringDecoded()).get("see").asText());
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
        assertEquals(APPLICATION_JSON, exc.getRequest().getHeader().getContentType());
    }

    @Test
    void contentTypeTestNoXml() {
        ti.setSrc("normal text");
        ti.init(router);
        ti.handleRequest(exc);
        assertEquals(TEXT_PLAIN, exc.getRequest().getHeader().getContentType());
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
        ti.setPretty(TRUE);
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
                { "foo": ${user()} }
                """);
        ti.init(router);
        ti.handleRequest(exc);

        assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("alice"));
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
        void escapeNull() throws URISyntaxException {
            exc = setJsonSample();
            ti.setContentType(APPLICATION_JSON_UTF8);
            ti.setSrc("${fn.jsonPath('$.a')}");
            ti.init(new DefaultRouter());
            ti.handleRequest(exc);
            assertEquals("null", exc.getRequest().getBodyAsStringDecoded());
        }

        private Exchange setJsonSample() throws URISyntaxException {
            return post("/foo").json("""
                    {"a":null}
                    """).buildExchange();
        }
    }

    private void setAndHandleRequest(String location) {
        ti.setLocation(Paths.get("src/test/resources/" + location).toString());
        ti.init(router);
        ti.handleRequest(exc);
    }


    private void invokeInterceptor(Exchange exchange, String template, String mimeType) {
        var i = new TemplateInterceptor();
        i.setSrc(template);
        i.setContentType(mimeType);
        i.init(router);
        i.handleRequest(exchange);
    }
}