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

package com.predic8.membrane.core.interceptor.soap2json;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Header.SOAP_ACTION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap2json.Soap2JsonInterceptor.OPERATION_PROPERTY;
import static org.junit.jupiter.api.Assertions.*;

class Json2SoapInterceptorTest {

    private static final String SHOP_WSDL = "classpath:/ws/two-operations-with-faults.wsdl";

    private static Json2SoapInterceptor json2Soap(String wsdl, String operation) {
        final var interceptor = new Json2SoapInterceptor();
        interceptor.setWsdl(wsdl);
        interceptor.setOperation(operation);
        interceptor.init(new DummyTestRouter());
        return interceptor;
    }

    private static Exchange requestExchange(String contentType, String body) throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().put("/articles").contentType(contentType).body(body).build());
        return exc;
    }

    private static Exchange responseExchange(int status, String contentType, String body, String operation) {
        final var exc = new Exchange(null);
        exc.setResponse(Response.statusCode(status).contentType(contentType).body(body).build());
        if (operation != null) exc.setProperty(OPERATION_PROPERTY, operation);
        return exc;
    }

    @Test
    void aRequestBecomesTheSoapRequestOfTheConfiguredOperation() throws Exception {
        final var exc = requestExchange(APPLICATION_JSON, "{\"quantity\": 2, \"articleId\": 42}");

        assertEquals(CONTINUE, json2Soap(SHOP_WSDL, "placeOrder").handleRequest(exc));

        final var request = exc.getRequest();
        final var body = request.getBodyAsStringDecoded();
        assertTrue(body.contains("placeOrder"), body);
        assertTrue(body.indexOf("articleId") < body.indexOf("quantity"), "fields in XSD order: " + body);
        assertEquals("POST", request.getMethod());
        assertEquals(TEXT_XML, request.getHeader().getContentType());
        assertEquals("\"https://example.com/shop/placeOrder\"", request.getHeader().getFirstValue(SOAP_ACTION));
    }

    @Test
    void aSoap12RequestCarriesTheActionInTheContentTypeAndNoSoapAction() throws Exception {
        final var exc = requestExchange(APPLICATION_JSON, "{\"name\": \"World\"}");
        exc.getRequest().getHeader().setSOAPAction("\"from-the-client\"");

        assertEquals(CONTINUE, json2Soap("classpath:/ws/hello-soap12.wsdl", "sayHello").handleRequest(exc));

        assertEquals("application/soap+xml; action=\"sayHello\"", exc.getRequest().getHeader().getContentType());
        assertNull(exc.getRequest().getHeader().getFirstValue(SOAP_ACTION));
        assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("http://www.w3.org/2003/05/soap-envelope"));
    }

    @Test
    void aResponseBecomesTheSoapResponseOfTheRecordedOperation() throws Exception {
        final var exc = responseExchange(201, APPLICATION_JSON, "{\"orderId\": 7}", "placeOrder");

        assertEquals(CONTINUE, json2Soap(SHOP_WSDL, null).handleResponse(exc));

        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("placeOrderResponse"), body);
        assertTrue(body.contains("<orderId>7</orderId>"), body);
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals(TEXT_XML, exc.getResponse().getHeader().getContentType());
    }

    @Test
    void theConfiguredOperationWinsOverTheRecordedOne() throws Exception {
        final var exc = responseExchange(200, APPLICATION_JSON, "{\"name\": \"Coffee\"}", "placeOrder");

        json2Soap(SHOP_WSDL, "getArticle").handleResponse(exc);

        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("getArticleResponse"));
    }

    @Test
    void aClientErrorOfTheBackendBecomesAClientFaultWithoutItsText() throws Exception {
        final var exc = responseExchange(404, APPLICATION_JSON, "{\"detail\": \"no row at db-host-3\"}", "getArticle");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, null).handleResponse(exc));

        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertEquals(500, exc.getResponse().getStatusCode());
        assertTrue(body.contains("soap:Client"), body);
        assertFalse(body.contains("db-host-3"), "the backend's text must not reach the client: " + body);
    }

    @Test
    void aServerErrorOfTheBackendBecomesAServerFaultEvenWithoutABody() throws Exception {
        final var exc = responseExchange(503, APPLICATION_JSON, "", "getArticle");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, null).handleResponse(exc));

        assertEquals(500, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("soap:Server"));
    }

    @Test
    void aSoap12FaultUsesReceiverAndTheSoap12MediaType() throws Exception {
        final var exc = responseExchange(500, APPLICATION_JSON, "{}", "sayHello");

        json2Soap("classpath:/ws/hello-soap12.wsdl", null).handleResponse(exc);

        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("soap:Receiver"));
        assertTrue(exc.getResponse().getHeader().getContentType().startsWith("application/soap+xml"));
    }

    @Test
    void withoutAnOperationTheRequestIsA500() throws Exception {
        final var exc = requestExchange(APPLICATION_JSON, "{}");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, null).handleRequest(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getHeader().getContentType().contains("problem+json"),
                "the JSON client gets problem details, not a fault");
    }

    @Test
    void anUnknownRecordedOperationInTheResponseIsAFault() throws Exception {
        final var exc = responseExchange(200, APPLICATION_JSON, "{}", "deleteEverything");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, null).handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("soap:Server"));
    }

    @Test
    void invalidJsonInTheRequestIsA400() throws Exception {
        final var exc = requestExchange(APPLICATION_JSON, "{\"articleId\": ");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, "placeOrder").handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void aKeyThatIsNoXmlNameInTheRequestIsA400() throws Exception {
        final var exc = requestExchange(APPLICATION_JSON, "{\"1 bad key\": 1}");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, "placeOrder").handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void aRequestThatIsNotJsonIsRejectedWith415() throws Exception {
        final var exc = requestExchange(TEXT_XML, "<placeOrder/>");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, "placeOrder").handleRequest(exc));
        assertEquals(415, exc.getResponse().getStatusCode());
    }

    @Test
    void invalidJsonFromTheBackendIsAFault() throws Exception {
        final var exc = responseExchange(200, APPLICATION_JSON, "{\"orderId\": ", "placeOrder");

        assertEquals(ABORT, json2Soap(SHOP_WSDL, null).handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("soap:Server"));
    }

    @Test
    void anEmptyRequestPassesThrough() throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().get("/articles/42").build());

        assertEquals(CONTINUE, json2Soap(SHOP_WSDL, "getArticle").handleRequest(exc));
        assertEquals("GET", exc.getRequest().getMethod());
    }

    @Test
    void aConfiguredOperationThePortDoesNotDeclareIsAConfigurationError() {
        final var e = assertThrows(ConfigurationException.class, () -> json2Soap(SHOP_WSDL, "deleteEverything"));
        assertTrue(e.getMessage().contains("getArticle, placeOrder"), e.getMessage());
    }
}
