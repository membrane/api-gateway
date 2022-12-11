package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.Assert.*;

public class OpenAPIInterceptorTest {

    ObjectMapper om = new ObjectMapper();
    Router router;

    OpenAPIProxy.Spec specInfoServers;
    OpenAPIProxy.Spec specInfo3Servers;
    OpenAPIProxy.Spec specCustomers;

    Exchange excGet = new Exchange(null);
    OpenAPIInterceptor interceptor1Server;
    OpenAPIInterceptor interceptor3Server;

    @Before
    public void setUp() throws Exception {
        router = new Router();

        specInfoServers = new OpenAPIProxy.Spec();
        specInfoServers.location = "src/test/resources/openapi/specs/info-servers.yml";

        specInfo3Servers = new OpenAPIProxy.Spec();
        specInfo3Servers.location = "src/test/resources/openapi/specs/info-3-servers.yml";

        specCustomers = new OpenAPIProxy.Spec();
        specCustomers.location = "src/test/resources/openapi/specs/customers.yml";

        excGet.setRequest(new Request.Builder().method("GET").build());

        interceptor1Server = new OpenAPIInterceptor(createProxy(router, specInfoServers));
        interceptor3Server = new OpenAPIInterceptor(createProxy(router, specInfo3Servers));
    }

    @Test
    public void getMatchingBasePathOneServer() {
        excGet.getRequest().setUri("/base/v2/foo");
        assertEquals("/base/v2", interceptor1Server.getMatchingBasePath(excGet));
    }

    @Test
    public void getMatchingBasePathMultipleServers() {
        excGet.getRequest().setUri("/foo/boo");
        assertEquals("/foo", interceptor3Server.getMatchingBasePath(excGet));
    }

    @Test
    public void nonMatchingBasePathMultipleServers() {
        excGet.getRequest().setUri("/goo/boo");
        assertNull(null, interceptor3Server.getMatchingBasePath(excGet));
    }

    @Test
    public void nonMatchingBasePathErrorMessage() throws Exception {
        excGet.getRequest().setUri("/goo/boo");
        assertEquals(RETURN, interceptor3Server.handleRequest(excGet));

        assertEquals(404, excGet.getResponse().getStatusCode());
        assertTrue(excGet.getResponse().getHeader().getContentType().contains("application/json"));
        excGet.getResponse().getBody().getContent();

        assertEquals("No matching API found!", getMapFromResponse(excGet).get("error"));
    }

    @Test
    public void destinations() throws Exception {
        excGet.getRequest().setUri("/foo/boo");
        assertEquals(CONTINUE, interceptor3Server.handleRequest(excGet));
        assertEquals(3,excGet.getDestinations().size());

        Collection<String> urls = new ArrayList<>();
        urls.add("https://localhost:3000/foo/boo");
        urls.add("https://localhost:4000/foo/boo");
        urls.add("https://localhost:5000/foo/boo");

        assertEquals(urls, excGet.getDestinations());
    }

    @Test
    public void destinationsTargetSet() throws Exception {
        excGet.getRequest().setUri("/foo/boo");
        OpenAPIProxy proxy = createProxy(router, specInfo3Servers);
        proxy.getTarget().setHost("api.predic8.de");
        assertEquals(CONTINUE, new OpenAPIInterceptor(proxy).handleRequest(excGet));
        assertEquals(0,excGet.getDestinations().size());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void validateRequest() throws Exception {

        specCustomers.validateRequests = true;

        Map<String,Object> customer = new HashMap<>();
        customer.put("id","CUST-7");
        customer.put("age",110);
        customer.put("foo",110);

        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("POST").url(new URIFactory(), "/customers").contentType("application/json").body(convert2JSON(customer)).build());

        assertEquals(RETURN, new OpenAPIInterceptor(createProxy(router,specCustomers)).handleRequest(exc));
        assertEquals(400,exc.getResponse().getStatusCode());

        assertEquals("POST", getMapFromResponse(exc).get("method"));
        testValidationResults(getMapFromResponse(exc), "REQUEST");
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void validateResponse() throws Exception {
        specCustomers.validateResponses = true;
        assertEquals("PUT", getMapFromResponse(callPut(specCustomers)).get("method"));
        testValidationResults(getMapFromResponse(callPut(specCustomers)), "RESPONSE");
    }

    @Test
    public void validateResponseLessDetails() throws Exception {
        specCustomers.validateResponses = true;
        specCustomers.validationDetails = false;
        assertEquals("Message validation failed!", getMapFromResponse(callPut(specCustomers)).get("error"));
    }

    @NotNull
    private Exchange callPut(OpenAPIProxy.Spec spec) throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("PUT").url(new URIFactory(), "/customers").contentType("application/json").build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, spec));

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        Map<String,Object> customer = new HashMap<>();
        customer.put("id","CUST-7");
        customer.put("age",110);
        customer.put("foo",110);

        exc.setResponse(Response.ResponseBuilder.newInstance().status(200,"OK").contentType("application/json").body(convert2JSON(customer)).build());

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