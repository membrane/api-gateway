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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.*;
import io.swagger.v3.parser.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPIPublisherInterceptorTest {

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    OpenAPIRecordFactory openAPIRecordFactory;
    OpenAPIPublisherInterceptor interceptor;
    Map<String, OpenAPIRecord> records;
    Exchange get = new Exchange(null);

    @BeforeEach
    public void setUp() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());
        router.setBaseLocation("");
        openAPIRecordFactory = new OpenAPIRecordFactory(router);
        OpenAPIProxy.Spec spec = new OpenAPIProxy.Spec();
        spec.setDir("src/test/resources/openapi/specs");
        records = openAPIRecordFactory.create(Collections.singletonList(spec));

        interceptor = new OpenAPIPublisherInterceptor(records);
        interceptor.init(router);

        get.setRequest(new Request.Builder().method("GET").build());
        get.setRule(new NullRule());
        get.setOriginalHostHeader("api.predic8.de:80");
    }

    @Test
    public void constuctor() {
        assertEquals(24, interceptor.apis.size());
        assertNotNull(interceptor.apis.get("references-test-v1-0"));
        assertNotNull(interceptor.apis.get("strings-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("extension-sample-v1-4"));
        assertNotNull(interceptor.apis.get("query-params-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("nested-objects-and-arrays-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("references-response-test-v1-0"));
    }

    @Test
    public void getApiDirectory() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals(24, TestUtils.getMapFromResponse(get).size());
    }

    @Test
    public void getSwaggerUI() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH_UI + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("html"));
    }

    @Test
    public void getApiById() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH  + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals("application/x-yaml", get.getResponse().getHeader().getContentType());
        assertEquals("Nested Objects and Arrays Test API", getJsonFromYamlResponse(get).get("info").get("title").textValue());
    }

    private JsonNode getJsonFromYamlResponse(Exchange exc) throws IOException {
        return omYaml.readTree(exc.getResponse().getBody().getContent());
    }

    @Test
    public void rewriteOpenAPIaccordingToRequestTest() throws MalformedURLException, URISyntaxException {
        OpenAPIRecord rec = records.get("servers-1-api-v1-0");
        interceptor.rewriteOpenAPIaccordingToRequest(get, rec);
        assertEquals("http://api.predic8.de/base/v2",rec.node.get("servers").get(0).get("url").asText());
        assertEquals("Test System",rec.node.get("servers").get(0).get("description").asText());
    }

    @Test
    public void rewriteOpenAPIaccordingToRequest3Servers() throws MalformedURLException, URISyntaxException {
        OpenAPIRecord rec = records.get("servers-3-api-v1-0");
        interceptor.rewriteOpenAPIaccordingToRequest(get, rec);
        assertEquals(3,rec.node.get("servers").size());
        assertEquals("http://api.predic8.de/foo",rec.node.get("servers").get(0).get("url").asText());
        assertEquals("http://api.predic8.de/foo",rec.node.get("servers").get(1).get("url").asText());
        assertEquals("http://api.predic8.de/foo",rec.node.get("servers").get(2).get("url").asText());
    }
}