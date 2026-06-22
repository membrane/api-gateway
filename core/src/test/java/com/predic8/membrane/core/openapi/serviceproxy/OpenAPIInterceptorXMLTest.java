/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.createProxy;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that when an XML request fails OpenAPI validation,
 * the error response is returned as an XML document (application/problem+xml).
 */
class OpenAPIInterceptorXMLTest {

    static final String VALID_ORDER = """
            <order>
              <id>4711</id>
              <customer>Anna Müller</customer>
            </order>
            """;

    /** id is a string, not an integer — should trigger a type-validation error. */
    static final String ORDER_BAD_ID_TYPE = """
            <order>
              <id>not-a-number</id>
              <customer>Anna Müller</customer>
            </order>
            """;

    /** required field <customer> is absent. */
    static final String ORDER_MISSING_CUSTOMER = """
            <order>
              <id>4711</id>
            </order>
            """;

    static final String MALFORMED_XML = "<order><unclosed>";

    Router router;
    OpenAPIInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new DummyTestRouter();

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = getPathFromResource("openapi/specs/xml/xml-order-validation.oas.yaml");

        interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    // -----------------------------------------------------------------------
    // Happy path: valid XML → 200/CONTINUE, no error response
    // -----------------------------------------------------------------------

    @Test
    void validXmlRequestContinues() throws Exception {
        Exchange exc = buildXmlPostExchange(VALID_ORDER);
        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertNull(exc.getResponse());
    }

    // -----------------------------------------------------------------------
    // Error responses are XML
    // -----------------------------------------------------------------------

    @Test
    void invalidXmlTypeError_responseIsXml() throws Exception {
        Exchange exc = buildXmlPostExchange(ORDER_BAD_ID_TYPE);
        assertEquals(RETURN, interceptor.handleRequest(exc));

        assertResponseIsXml(exc.getResponse());
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void missingRequiredField_responseIsXml() throws Exception {
        Exchange exc = buildXmlPostExchange(ORDER_MISSING_CUSTOMER);
        assertEquals(RETURN, interceptor.handleRequest(exc));

        assertResponseIsXml(exc.getResponse());
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void malformedXml_responseIsXml() throws Exception {
        Exchange exc = buildXmlPostExchange(MALFORMED_XML);
        assertEquals(RETURN, interceptor.handleRequest(exc));

        assertResponseIsXml(exc.getResponse());
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Error XML document contains problem-details fields
    // -----------------------------------------------------------------------

    @Test
    void errorResponseContainsTitleElement() throws Exception {
        Exchange exc = buildXmlPostExchange(ORDER_BAD_ID_TYPE);
        interceptor.handleRequest(exc);

        Document doc = parseResponseXml(exc.getResponse());
        assertNotNull(doc.getElementsByTagName("title").item(0),
                "Expected <title> element in problem-details XML");
    }

    @Test
    void errorResponseContainsStatusElement() throws Exception {
        Exchange exc = buildXmlPostExchange(ORDER_MISSING_CUSTOMER);
        interceptor.handleRequest(exc);

        Document doc = parseResponseXml(exc.getResponse());
        Node statusNode = doc.getElementsByTagName("status").item(0);
        assertNotNull(statusNode, "Expected <status> element in problem-details XML");
        assertEquals("400", statusNode.getTextContent());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Exchange buildXmlPostExchange(String xmlBody) throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/orders");
        exc.setRequest(
                new Request.Builder()
                        .method("POST")
                        .url(new URIFactory(), "/orders")
                        .contentType(APPLICATION_XML)
                        .body(xmlBody)
                        .build());
        return exc;
    }

    private static void assertResponseIsXml(Response response) {
        assertNotNull(response, "Response must not be null");
        String contentType = response.getHeader().getContentType();
        assertNotNull(contentType, "Content-Type must be set on error response");
        assertTrue(contentType.contains("xml"),
                "Expected XML content type but got: " + contentType);
    }

    private static Document parseResponseXml(Response response) throws Exception {
        byte[] body = response.getBody().getContent();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(body));
    }
}
