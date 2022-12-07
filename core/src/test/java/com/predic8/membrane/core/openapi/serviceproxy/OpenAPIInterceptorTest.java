package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.JsonUtil.convert2JSON;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.Assert.*;

public class OpenAPIInterceptorTest {

    ObjectMapper om = new ObjectMapper();
    Router router;

    @Before
    public void setUp() {
        router = new Router();
    }

    @Test
    public void getMatchingBasePathOneServer() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/info-servers.yml";

        Exchange exc = new Exchange(null);
        exc.setRequest(new Request.Builder().method("GET").url(new URIFactory(), "/base/v2/foo").build());

        assertEquals("/base/v2", new OpenAPIInterceptor(createProxy(router,spec)).getMatchingBasePath(exc));
    }

    @Test
    public void getMatchingBasePathMultipleServers() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/info-3-servers.yml";

        Exchange exc = new Exchange(null);
        exc.setRequest(new Request.Builder().method("GET").url(new URIFactory(), "/foo/boo").build());

        assertEquals("/foo", new OpenAPIInterceptor(createProxy(router,spec)).getMatchingBasePath(exc));
    }

    @Test
    public void nonMatchingBasePathMultipleServers() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/info-3-servers.yml";

        Exchange exc = new Exchange(null);
        exc.setRequest(new Request.Builder().method("GET").url(new URIFactory(), "/goo/boo").build());

        assertNull(null, new OpenAPIInterceptor(createProxy(router,spec)).getMatchingBasePath(exc));
    }

    @Test
    public void nonMatchingBasePathErrorMessage() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/info-3-servers.yml";

        Exchange exc = new Exchange(null);
        exc.setRequest(new Request.Builder().method("GET").url(new URIFactory(), "/goo/boo").build());

        Outcome outcome = new OpenAPIInterceptor(createProxy(router,spec)).handleRequest(exc);

        assertEquals(RETURN, outcome);

        assertEquals(404, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getHeader().getContentType().contains("application/json"));
        exc.getResponse().getBody().getContent();

        JsonNode node = om.readValue(exc.getResponse().getBody().getContent(), JsonNode.class);
        assertEquals("No matching API found!",node.get("error").asText());
    }

    @Test
    public void destinations() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/info-3-servers.yml";

        Exchange exc = new Exchange(null);
        exc.setRequest(new Request.Builder().method("GET").url(new URIFactory(), "/foo/boo").build());

        Outcome outcome = new OpenAPIInterceptor(createProxy(router,spec)).handleRequest(exc);

        assertEquals(CONTINUE, outcome);

        assertEquals(3,exc.getDestinations().size());

        List<String> urls = new ArrayList<>();
        urls.add("https://localhost:3000/foo/boo");
        urls.add("https://localhost:4000/foo/boo");
        urls.add("https://localhost:5000/foo/boo");

        assertEquals(urls, exc.getDestinations());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void validateRequest() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/customers.yml";
        spec.validateRequests = true;

        Map<String,Object> customer = new HashMap<>();
        customer.put("id","CUST-7");
        customer.put("age",110);
        customer.put("foo",110);

        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");
        exc.setRequest(new Request.Builder().method("POST").url(new URIFactory(), "/customers").contentType("application/json").body(convert2JSON(customer)).build());

        Outcome outcome = new OpenAPIInterceptor(createProxy(router,spec)).handleRequest(exc);

        assertEquals(RETURN, outcome);
        assertEquals(400,exc.getResponse().getStatusCode());

        Map errors = om.readValue(exc.getResponse().getBody().getContent(), Map.class);
        assertEquals("POST", errors.get("method"));
        testValidationResults(errors, "REQUEST");
    }



    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void validateResponse() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/customers.yml";
        spec.validateResponses = true;

        Exchange exc = callEndpoint(spec);

        Map errors = om.readValue(exc.getResponse().getBody().getContent(), Map.class);
        assertEquals("PUT", errors.get("method"));
        testValidationResults(errors, "RESPONSE");
    }

    @Test
    @SuppressWarnings({"rawtypes"})
    public void validateResponseLessDetails() throws Exception {

        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.location = "src/test/resources/openapi/customers.yml";
        spec.validateResponses = true;
        spec.validationDetails = false;

        Exchange exc = callEndpoint(spec);

        Map errors = om.readValue(exc.getResponse().getBody().getContent(), Map.class);

        assertEquals("Message validation failed!", errors.get("error"));
    }

    @NotNull
    private Exchange callEndpoint(OpenAPIProxy.Spec spec) throws Exception {
        Exchange exc = new Exchange(null);
        exc.setOriginalRequestUri("/customers");

        exc.setRequest(new Request.Builder().method("PUT").url(new URIFactory(), "/customers").contentType("application/json").build());

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, spec));

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        Map<String,Object> customer = new HashMap<>();
        customer.put("id","CUST-7");
        customer.put("age",110);
        customer.put("foo",110);

        Response response = Response.ResponseBuilder.newInstance().status(200,"OK").contentType("application/json").body(convert2JSON(customer)).build();
        exc.setResponse(response);

        Outcome outcome = interceptor.handleResponse(exc);

        assertEquals(RETURN, outcome);
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