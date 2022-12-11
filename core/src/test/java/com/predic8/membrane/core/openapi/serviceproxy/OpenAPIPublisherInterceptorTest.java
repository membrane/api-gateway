package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.parser.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.Assert.*;

public class OpenAPIPublisherInterceptorTest {

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    OpenAPIPublisherInterceptor interceptor;

    Exchange get = new Exchange(null);

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        Router router = new Router();
        router.setBaseLocation("");
        OpenAPIRecordFactory factory = new OpenAPIRecordFactory(router);
        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.setDir("src/test/resources/openapi/specs");
        Map<String, OpenAPIRecord> records = factory.create(Collections.singletonList(spec));

        interceptor = new OpenAPIPublisherInterceptor(records);

        get.setRequest(new Request.Builder().method("GET").build());
    }

    @Test
    public void constuctor() {
        assertEquals(23, interceptor.apis.size());
        assertNotNull(interceptor.apis.get("references-test-v1-0"));
        assertNotNull(interceptor.apis.get("strings-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("x-membrane-extension-sample-v1-4"));
        assertNotNull(interceptor.apis.get("query-params-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("nested-objects-and-arrays-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("references-response-test-v1-0"));
    }

    @Test
    public void getApiDirectory() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals(23, TestUtils.getMapFromResponse(get).size());
    }

    @Test
    public void getSwaggerUI() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH_UI + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("html"));
    }

    @Test
    public void getApiById() throws Exception {
        get.getRequest().setUri("/openapi-spec/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals("application/x-yaml", get.getResponse().getHeader().getContentType());
        assertEquals("Nested Objects and Arrays Test API", getJsonFromYamlResponse(get).get("info").get("title").textValue());
    }

    private JsonNode getJsonFromYamlResponse(Exchange exc) throws IOException {
        return omYaml.readTree(exc.getResponse().getBody().getContent());
    }
}