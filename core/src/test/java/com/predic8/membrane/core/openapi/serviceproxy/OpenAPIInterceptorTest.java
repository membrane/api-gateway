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

import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.openapi.OpenAPIValidator;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.URIFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.NO;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.openapi.util.JsonTestUtil.convert2JSON;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.createProxy;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.getMapFromResponse;
import static com.predic8.membrane.core.util.ProblemDetailsTestUtil.parse;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;

class OpenAPIInterceptorTest {

    Router router;
    OpenAPISpec specInfoServers;
    OpenAPISpec specInfo3Servers;
    OpenAPISpec specCustomers;

    final Exchange exc = new Exchange(null);
    OpenAPIInterceptor interceptor1Server;
    OpenAPIInterceptor interceptor3Server;

    @BeforeEach
    void setUp() {
        router = new DummyTestRouter();

        specInfoServers = new OpenAPISpec();
        specInfoServers.location = getPathFromResource("openapi/specs/info-servers.yml");

        specInfo3Servers = new OpenAPISpec();
        specInfo3Servers.location = getPathFromResource("openapi/specs/info-3-servers.yml");

        specCustomers = new OpenAPISpec();
        specCustomers.location = getPathFromResource( "openapi/specs/customers.yml");

        exc.setRequest(new Request.Builder().method("GET").build());

        interceptor1Server = new OpenAPIInterceptor(createProxy(router, specInfoServers));
        interceptor1Server.init(router);
        interceptor3Server = new OpenAPIInterceptor(createProxy(router, specInfo3Servers));
        interceptor3Server.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void getMatchingBasePathOneServer() {
        exc.getRequest().setUri("/base/v2/foo");
        assertEquals("/base/v2/", interceptor1Server.getMatchingBasePath(exc));
    }

    @Test
    void getMatchingBasePathMultipleServers() {
        exc.getRequest().setUri("/foo/boo");
        assertEquals("/foo/", interceptor3Server.getMatchingBasePath(exc));
    }

    @Test
    void getMatchingBasePathExactServerPath() {
        exc.getRequest().setUri("/foo/");
        assertEquals("/foo/", interceptor3Server.getMatchingBasePath(exc));
    }

    @Test
    void nonMatchingBasePathMultipleServers() {
        exc.getRequest().setUri("/goo/boo");
        assertNull( interceptor3Server.getMatchingBasePath(exc));
    }

    @Test
    void nonMatchingBasePathErrorMessage() throws Exception {
        exc.getRequest().setUri("/goo/boo");
        assertEquals(RETURN, interceptor3Server.handleRequest(exc));

        assertEquals(404, exc.getResponse().getStatusCode());
        exc.getResponse().getBody().getContent();

        ProblemDetails pd = parse(exc.getResponse());

        assertEquals("No matching API found!", pd.getTitle());
        assertEquals("https://membrane-api.io/problems/user", pd.getType());
    }

    @Test
    void destinations() {
        exc.getRequest().setUri("/foo/boo");
        exc.setOriginalRequestUri("/foo/boo");

        assertEquals(CONTINUE, interceptor3Server.handleRequest(exc));
        assertEquals(3, exc.getDestinations().size());

        Collection<String> urls = new ArrayList<>();
        urls.add("https://localhost:3000/foo/boo");
        urls.add("https://localhost:4000/foo/boo");
        urls.add("https://localhost:5000/foo/boo");

        assertEquals(urls, exc.getDestinations());
    }

    @Test
    void destinationsTargetSet() {
        exc.getRequest().setUri("/foo/boo");
        exc.setOriginalRequestUri("/foo/boo");
        APIProxy proxy = createProxy(router, specInfo3Servers);
        proxy.getTarget().setHost("api.predic8.de");
        OpenAPIInterceptor openAPIInterceptor = new OpenAPIInterceptor(proxy);
        openAPIInterceptor.init(router);
        assertEquals(CONTINUE, openAPIInterceptor.handleRequest(exc));
        assertEquals(0, exc.getDestinations().size());
    }

    @Test
    void validateRequest() throws Exception {

        specCustomers.validateRequests = YES;

        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("POST").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).body(convert2JSON(invalidCustomer())).build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, specCustomers));
        interceptor.init(router);
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(400,exc.getResponse().getStatusCode());

        assertEquals("POST", getMapFromResponse(exc).get("validation").get("method"));
        testValidationResults(getMapFromResponse(exc), "REQUEST");
    }

    @Test
    void validateResponse() throws Exception {
        specCustomers.validateResponses = YES;
        Exchange exc = callPut(specCustomers);
        assertEquals("PUT",  getMapFromResponse(exc).get("validation").get("method"));
        testValidationResults(getMapFromResponse(exc), "RESPONSE");
    }

    @Test
    void requestValidationDisabledResponseValidationEnabled() throws Exception {
        specCustomers.validateResponses = YES;

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, specCustomers));
        interceptor.init(router);

        Exchange requestExc = new Exchange(null);
        requestExc.setOriginalRequestUri("/customers");
        requestExc.setRequest(new Request.Builder().method("POST").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).body(convert2JSON(invalidCustomer())).build());

        assertEquals(CONTINUE, interceptor.handleRequest(requestExc));
        assertNull(requestExc.getResponse());

        Exchange responseExc = new Exchange(null);
        responseExc.setOriginalRequestUri("/customers");
        responseExc.setRequest(new Request.Builder().method("PUT").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).build());

        assertEquals(CONTINUE, interceptor.handleRequest(responseExc));

        responseExc.setResponse(Response.ok().contentType(APPLICATION_JSON).body(convert2JSON(invalidCustomer())).build());

        assertEquals(RETURN, interceptor.handleResponse(responseExc));
        assertEquals(500, responseExc.getResponse().getStatusCode());
        testValidationResults(getMapFromResponse(responseExc), "RESPONSE");
    }

    @Test
    void requestValidationDisabledResponseValidationEnabled_wrongPathBlocksRequest() throws Exception {
        specCustomers.validateResponses = YES;

        Exchange requestExc = new Exchange(null);
        requestExc.setOriginalRequestUri("/does-not-exist");
        requestExc.setRequest(new Request.Builder().method("GET").url(new URIFactory(), "/does-not-exist").build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, specCustomers));
        interceptor.init(router);

        assertEquals(RETURN, interceptor.handleRequest(requestExc));
        assertEquals(404, requestExc.getResponse().getStatusCode());
        assertEquals("/does-not-exist", getMapFromResponse(requestExc).get("validation").get("path"));
        assertTrue(getMapFromResponse(requestExc).get("validation").containsKey("errors"));
        assertTrue(((Map<?, ?>) getMapFromResponse(requestExc).get("validation").get("errors")).containsKey("REQUEST/PATH"));
    }

    @Test
    void requestValidationDisabledResponseValidationEnabled_wrongMethodBlocksRequest() throws Exception {
        specCustomers.validateResponses = YES;

        Exchange requestExc = new Exchange(null);
        requestExc.setOriginalRequestUri("/customers");
        requestExc.setRequest(new Request.Builder().method("PATCH").url(new URIFactory(), "/customers").build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, specCustomers));
        interceptor.init(router);

        assertEquals(RETURN, interceptor.handleRequest(requestExc));
        assertEquals(405, requestExc.getResponse().getStatusCode());
        assertEquals("PATCH", getMapFromResponse(requestExc).get("validation").get("method"));
        assertTrue(((Map<?, ?>) getMapFromResponse(requestExc).get("validation").get("errors")).containsKey("REQUEST/METHOD"));
    }

    @Test
    void requestValidationEnabledResponseValidationDisabled() throws Exception {
        specCustomers.validateRequests = YES;

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, specCustomers));
        interceptor.init(router);

        Exchange requestExc = new Exchange(null);
        requestExc.setOriginalRequestUri("/customers");
        requestExc.setRequest(new Request.Builder().method("POST").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).body(convert2JSON(invalidCustomer())).build());

        assertEquals(RETURN, interceptor.handleRequest(requestExc));
        assertEquals(400, requestExc.getResponse().getStatusCode());
        testValidationResults(getMapFromResponse(requestExc), "REQUEST");

        Exchange responseExc = new Exchange(null);
        responseExc.setOriginalRequestUri("/customers");
        responseExc.setRequest(new Request.Builder().method("PUT").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).build());

        assertEquals(CONTINUE, interceptor.handleRequest(responseExc));

        responseExc.setResponse(Response.ok().contentType(APPLICATION_JSON).body(convert2JSON(invalidCustomer())).build());

        assertEquals(CONTINUE, interceptor.handleResponse(responseExc));
        assertEquals(200, responseExc.getResponse().getStatusCode());
    }

    @Test
    void storesValidationPlanForResponseChecks() throws Exception {
        specCustomers.validateResponses = YES;

        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("PUT").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, specCustomers));
        interceptor.init(router);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertNotNull(exc.getProperty(OpenAPIInterceptor.OPENAPI_VALIDATOR_CTX_PROPERTY, OpenAPIValidator.ValidationPlan.class));
    }

    @Test
    void validateResponseLessDetails() throws Exception {
        specCustomers.validateResponses = YES;
        specCustomers.validationDetails = NO;
        assertEquals("Message validation failed!", getMapFromResponse(callPut(specCustomers)).get("validation").get("error"));
    }

    @NotNull
    private Exchange callPut(OpenAPISpec spec) throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("PUT").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        exc.setResponse(Response.ok().contentType(APPLICATION_JSON).body(convert2JSON(invalidCustomer())).build());
        assertEquals(RETURN, interceptor.handleResponse(exc));
        assertEquals(500,exc.getResponse().getStatusCode());
        return exc;
    }

    private Map<String, Object> invalidCustomer() {
        Map<String, Object> customer = new HashMap<>();
        customer.put("id", "CUST-7");
        customer.put("age", 110);
        customer.put("foo", 110);
        return customer;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void testValidationResults(Map<String, Map<String,Object>> errors1, String direction) {
        Map<String,Object> errors = errors1.get("validation");

        assertEquals("/customers", errors.get("uriTemplate"));
        assertEquals("/customers", errors.get("path"));

        Map<String,Object> validationErrors = (Map<String, Object>) errors.get("errors");

        assertEquals(3,validationErrors.size());

        Map<String,Object> m1 = (Map<String, Object>) ((List)validationErrors.get( direction + "/BODY")).getFirst();
        assertEquals("Customer",m1.get("complexType"));
        assertEquals("object",m1.get("schemaType"));

        assertTrue(((String)m1.get("message")).contains("additional properties"));

        Map<String,Object> m2 = (Map<String, Object>) ((List)validationErrors.get(direction + "/BODY#/firstName")).getFirst();

        assertEquals("Customer",m2.get("complexType"));
        assertEquals("object",m2.get("schemaType"));
        assertTrue(((String)m2.get("message")).contains("missing"));

        Map<String,Object> m3 = (Map<String, Object>) ((List)validationErrors.get(direction + "/BODY#/age")).getFirst();

        assertEquals("Customer",m3.get("complexType"));
        assertEquals("integer",m3.get("schemaType"));
        assertTrue(((String)m3.get("message")).contains("maximum"));
    }
}
