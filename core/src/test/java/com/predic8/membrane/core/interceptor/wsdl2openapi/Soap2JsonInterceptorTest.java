/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.*;

class Soap2JsonInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GET_ARTICLE_REQUEST = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <getArticle xmlns="https://example.com/shop"><id xmlns="">42</id></getArticle>
              </s:Body>
            </s:Envelope>""";

    private static final String GET_ARTICLE_RESPONSE = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <ns:getArticleResponse xmlns:ns="https://example.com/shop">
                  <name>Coffee</name>
                  <price>4.5</price>
                  <available>true</available>
                  <tag>organic</tag>
                </ns:getArticleResponse>
              </s:Body>
            </s:Envelope>""";

    private static final String PLACE_ORDER_RESPONSE_SOAP12 = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
              <s:Body>
                <ns:placeOrderResponse xmlns:ns="https://example.com/shop"><orderId>7</orderId></ns:placeOrderResponse>
              </s:Body>
            </s:Envelope>""";

    private static final String OUT_OF_STOCK_FAULT = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <s:Fault>
                  <faultcode>s:Server</faultcode>
                  <faultstring>Out of stock at internal-host-7</faultstring>
                  <detail>
                    <ns:outOfStock xmlns:ns="https://example.com/shop"><available>3</available></ns:outOfStock>
                  </detail>
                </s:Fault>
              </s:Body>
            </s:Envelope>""";

    private static final String UNDECLARED = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body><ns:deleteEverything xmlns:ns="https://example.com/shop"/></s:Body>
            </s:Envelope>""";

    private Soap2JsonInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new Soap2JsonInterceptor();
        interceptor.setWsdl("classpath:/ws/two-operations-with-faults.wsdl");
        interceptor.init(new DummyTestRouter());
    }

    private static Exchange requestExchange(String soap) throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/shop").contentType(TEXT_XML_UTF8).body(soap).build());
        return exc;
    }

    private static Exchange responseExchange(String contentType, String soap) {
        final var exc = new Exchange(null);
        exc.setResponse(Response.ok().contentType(contentType).body(soap).build());
        return exc;
    }

    private static JsonNode json(com.predic8.membrane.core.http.Message msg) throws Exception {
        return MAPPER.readTree(msg.getBodyAsStringDecoded());
    }

    @Test
    void aRequestIsConvertedWithTheSchemaOfItsMessage() throws Exception {
        final var exc = requestExchange(GET_ARTICLE_REQUEST);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(APPLICATION_JSON, exc.getRequest().getHeader().getContentType());
        assertEquals(42, json(exc.getRequest()).get("id").asInt());
        assertTrue(json(exc.getRequest()).get("id").isNumber(), "xsd:int must become a JSON number");
    }

    @Test
    void aResponseIsConvertedWithTypedValuesAndArrays() throws Exception {
        final var exc = responseExchange(TEXT_XML_UTF8, GET_ARTICLE_RESPONSE);

        assertEquals(CONTINUE, interceptor.handleResponse(exc));
        final var json = json(exc.getResponse());
        assertEquals("Coffee", json.get("name").asText());
        assertTrue(json.get("price").isNumber());
        assertTrue(json.get("available").isBoolean());
        assertTrue(json.get("tag").isArray(), "maxOccurs=unbounded must stay an array with a single item: " + json);
        assertEquals(200, exc.getResponse().getStatusCode());
    }

    @Test
    void aSoap12ResponseIsConverted() throws Exception {
        final var exc = responseExchange("application/soap+xml; charset=utf-8", PLACE_ORDER_RESPONSE_SOAP12);

        assertEquals(CONTINUE, interceptor.handleResponse(exc));
        assertEquals(7, json(exc.getResponse()).get("orderId").asLong());
    }

    @Test
    void aFaultFromTheServiceBecomesA500WithTypedDetailsAndNoFaultString() throws Exception {
        final var exc = responseExchange(TEXT_XML_UTF8, OUT_OF_STOCK_FAULT);
        interceptor.getRouter().getConfiguration().setProduction(true);

        assertEquals(ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertEquals(3, MAPPER.readTree(body).get("details").get("outOfStock").get("available").asInt());
        assertFalse(body.contains("internal-host-7"), "the fault string must not reach the client: " + body);
    }

    @Test
    void aFaultInTheRequestFlowIsAClientError() throws Exception {
        final var exc = requestExchange(OUT_OF_STOCK_FAULT);

        assertEquals(ABORT, interceptor.handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void anUndeclaredRequestElementIsRejectedWith400NamingIt() throws Exception {
        final var exc = requestExchange(UNDECLARED);
        interceptor.getRouter().getConfiguration().setProduction(true);

        assertEquals(ABORT, interceptor.handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("deleteEverything"));
    }

    @Test
    void anUndeclaredResponseElementIsA500ThatDoesNotEchoTheBackend() throws Exception {
        final var exc = responseExchange(TEXT_XML_UTF8, UNDECLARED);
        interceptor.getRouter().getConfiguration().setProduction(true);

        assertEquals(ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertFalse(exc.getResponse().getBodyAsStringDecoded().contains("deleteEverything"));
    }

    @Test
    void malformedXmlInTheResponseIsA500() throws Exception {
        final var exc = responseExchange(TEXT_XML_UTF8, "<s:Envelope");

        assertEquals(ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    @Test
    void aRequestThatIsNotXmlIsRejectedWith415() throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/shop").contentType(APPLICATION_JSON).body("{\"id\":42}").build());

        assertEquals(ABORT, interceptor.handleRequest(exc));
        assertEquals(415, exc.getResponse().getStatusCode());
    }

    @Test
    void aResponseThatIsNotXmlIsA500ThatDoesNotEchoTheBackend() throws Exception {
        final var exc = responseExchange("text/x-internal-format", "secret");
        interceptor.getRouter().getConfiguration().setProduction(true);

        assertEquals(ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertFalse(exc.getResponse().getBodyAsStringDecoded().contains("x-internal-format"));
    }

    @Test
    void anMtomPackageIsNotConverted() throws Exception {
        final var exc = responseExchange("multipart/related; type=\"application/xop+xml\"; boundary=b", "--b--");

        assertEquals(ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    @Test
    void anEmptyBodyPassesThrough() throws Exception {
        final var exc = responseExchange(TEXT_XML_UTF8, "");

        assertEquals(CONTINUE, interceptor.handleResponse(exc));
        assertEquals(TEXT_XML_UTF8, exc.getResponse().getHeader().getContentType());
    }
}
