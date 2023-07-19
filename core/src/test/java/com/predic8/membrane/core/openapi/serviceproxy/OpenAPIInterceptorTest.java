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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPIInterceptorTest {

    Router router;

    OpenAPISpec specInfoServers;
    OpenAPISpec specInfo3Servers;
    OpenAPISpec specCustomers;

    Exchange exc = new Exchange(null);
    OpenAPIInterceptor interceptor1Server;
    OpenAPIInterceptor interceptor3Server;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        specInfoServers = new OpenAPISpec();
        specInfoServers.location = "src/test/resources/openapi/specs/info-servers.yml";

        specInfo3Servers = new OpenAPISpec();
        specInfo3Servers.location = "src/test/resources/openapi/specs/info-3-servers.yml";

        specCustomers = new OpenAPISpec();
        specCustomers.location = "src/test/resources/openapi/specs/customers.yml";

        exc.setRequest(new Request.Builder().method("GET").build());

        interceptor1Server = new OpenAPIInterceptor(createProxy(router, specInfoServers));
        interceptor1Server.init(router);
        interceptor3Server = new OpenAPIInterceptor(createProxy(router, specInfo3Servers));
        interceptor3Server.init(router);
    }

    @Test
    public void getMatchingBasePathOneServer() {
        exc.getRequest().setUri("/base/v2/foo");
        assertEquals("/base/v2", interceptor1Server.getMatchingBasePath(exc));
    }

    @Test
    public void getMatchingBasePathMultipleServers() {
        exc.getRequest().setUri("/foo/boo");
        assertEquals("/foo", interceptor3Server.getMatchingBasePath(exc));
    }

    @Test
    public void nonMatchingBasePathMultipleServers() {
        exc.getRequest().setUri("/goo/boo");
        assertNull(null, interceptor3Server.getMatchingBasePath(exc));
    }

    @Test
    public void nonMatchingBasePathErrorMessage() throws Exception {
        exc.getRequest().setUri("/goo/boo");
        assertEquals(RETURN, interceptor3Server.handleRequest(exc));

        assertEquals(404, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getHeader().getContentType().contains(APPLICATION_PROBLEM_JSON));
        exc.getResponse().getBody().getContent();

        System.out.println("getMapFromResponse(exc) = " + getMapFromResponse(exc));
        assertEquals("No matching API found!", getMapFromResponse(exc).get("title"));
        assertEquals("http://membrane-api.io/error/not-found", getMapFromResponse(exc).get("type"));
    }

    @Test
    public void destinations() throws Exception {
        exc.getRequest().setUri("/foo/boo");
        assertEquals(CONTINUE, interceptor3Server.handleRequest(exc));
        assertEquals(3, exc.getDestinations().size());

        Collection<String> urls = new ArrayList<>();
        urls.add("https://localhost:3000/foo/boo");
        urls.add("https://localhost:4000/foo/boo");
        urls.add("https://localhost:5000/foo/boo");

        assertEquals(urls, exc.getDestinations());
    }

    @Test
    public void destinationsTargetSet() throws Exception {
        exc.getRequest().setUri("/foo/boo");
        APIProxy proxy = createProxy(router, specInfo3Servers);
        proxy.getTarget().setHost("api.predic8.de");
        assertEquals(CONTINUE, new OpenAPIInterceptor(proxy).handleRequest(exc));
        assertEquals(0, exc.getDestinations().size());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void validateRequest() throws Exception {

        specCustomers.validateRequests = OpenAPISpec.YesNoOpenAPIOption.YES;

        Map<String,Object> customer = new HashMap<>();
        customer.put("id","CUST-7");
        customer.put("age",110);
        customer.put("foo",110);

        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("POST").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).body(convert2JSON(customer)).build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, specCustomers));
        interceptor.init(router);
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(400,exc.getResponse().getStatusCode());

        assertEquals("POST", getMapFromResponse(exc).get("method"));
        testValidationResults(getMapFromResponse(exc), "REQUEST");
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void validateResponse() throws Exception {
        specCustomers.validateResponses = OpenAPISpec.YesNoOpenAPIOption.YES;
        assertEquals("PUT", getMapFromResponse(callPut(specCustomers)).get("method"));
        testValidationResults(getMapFromResponse(callPut(specCustomers)), "RESPONSE");
    }

    @Test
    public void validateResponseLessDetails() throws Exception {
        specCustomers.validateResponses = OpenAPISpec.YesNoOpenAPIOption.YES;
        specCustomers.validationDetails = OpenAPISpec.YesNoOpenAPIOption.NO;
        assertEquals("Message validation failed!", getMapFromResponse(callPut(specCustomers)).get("error"));
    }

    @NotNull
    private Exchange callPut(OpenAPISpec spec) throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("PUT").url(new URIFactory(), "/customers").contentType(APPLICATION_JSON).build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        Map<String,Object> customer = new HashMap<>();
        customer.put("id","CUST-7");
        customer.put("age",110);
        customer.put("foo",110);

        exc.setResponse(Response.ResponseBuilder.newInstance().status(200,"OK").contentType(APPLICATION_JSON).body(convert2JSON(customer)).build());

        assertEquals(RETURN, interceptor.handleResponse(exc));
        assertEquals(500,exc.getResponse().getStatusCode());
        return exc;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void testValidationResults(Map<String, Object> errors, String direction) {

        assertEquals("/customers", errors.get("uriTemplate"));
        assertEquals("/customers", errors.get("path"));

        Map<String,Object> validationErrors = (Map<String, Object>) errors.get("validationErrors");

        assertEquals(3,validationErrors.size());

        Map<String,Object> m1 = (Map<String, Object>) ((List)validationErrors.get( direction + "/BODY")).get(0);
        assertEquals("Customer",m1.get("complexType"));
        assertEquals("object",m1.get("schemaType"));

        assertTrue(((String)m1.get("message")).contains("additional properties"));

        Map<String,Object> m2 = (Map<String, Object>) ((List)validationErrors.get(direction + "/BODY#/firstName")).get(0);

        assertEquals("Customer",m2.get("complexType"));
        assertEquals("object",m2.get("schemaType"));
        assertTrue(((String)m2.get("message")).contains("missing"));

        Map<String,Object> m3 = (Map<String, Object>) ((List)validationErrors.get(direction + "/BODY#/age")).get(0);

        assertEquals("Customer",m3.get("complexType"));
        assertEquals("integer",m3.get("schemaType"));
        assertTrue(((String)m3.get("message")).contains("maximum"));
    }
}