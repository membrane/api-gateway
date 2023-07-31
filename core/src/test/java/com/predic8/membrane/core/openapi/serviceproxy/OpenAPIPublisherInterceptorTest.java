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
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.*;
import io.swagger.v3.parser.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPIPublisherInterceptorTest {

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();
    private final ObjectMapper om = new ObjectMapper();

    private static final String metaOld = "/api-doc";
    private static final String uiOld = "/api-doc/ui";

    OpenAPIRecordFactory openAPIRecordFactory;
    OpenAPIPublisherInterceptor interceptor;
    Map<String, OpenAPIRecord> records;
    Exchange get = new Exchange(null);

    @BeforeEach
    void setUp() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());
        router.setBaseLocation("");
        openAPIRecordFactory = new OpenAPIRecordFactory(router);
        OpenAPISpec spec = new OpenAPISpec();
        spec.setDir("src/test/resources/openapi/specs");
        records = openAPIRecordFactory.create(Collections.singletonList(spec));

        interceptor = new OpenAPIPublisherInterceptor( records);
        interceptor.init(router);

        get.setRequest(new Request.Builder().method("GET").build());
        get.setRule(new NullRule());
        get.setOriginalHostHeader("api.predic8.de:80");
    }

    @Test
    public void constuctor() {
        assertTrue(interceptor.apis.size() >= 27);
        assertNotNull(interceptor.apis.get("references-test-v1-0"));
        assertNotNull(interceptor.apis.get("strings-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("extension-sample-v1-4"));
        assertNotNull(interceptor.apis.get("query-params-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("nested-objects-and-arrays-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("references-response-test-v1-0"));
    }

    @Test
    public void getApiDirectoryOld() throws Exception {
        get.getRequest().setUri(metaOld);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(TestUtils.getMapFromResponse(get).size() >= 27);
    }

    @Test
    public void getApiDirectory() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(TestUtils.getMapFromResponse(get).size() >= 27);
    }

    @Test
    public void getHTMLOverviewOld() throws Exception {
        get.getRequest().setUri(metaOld);
        Header header = new Header();
        header.setAccept("html");
        get.getRequest().setHeader(header);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("<a href=\"" + uiOld + "/servers-1-api-v1-0\">Servers 1 API</a>"));
    }

    @Test
    public void getHTMLOverview() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH);
        Header header = new Header();
        header.setAccept("html");
        get.getRequest().setHeader(header);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("<a href=\"" + OpenAPIPublisherInterceptor.PATH_UI + "/servers-1-api-v1-0\">Servers 1 API</a>"));
    }

    @Test
    public void getSwaggerUIOld() throws Exception {
        get.getRequest().setUri(uiOld + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("html"));
    }

    @Test
    public void getSwaggerUI() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH_UI + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("html"));
    }

    @Test
    public void getSwaggerUIWrongIdOld() throws Exception {
        get.getRequest().setUri(uiOld + "/wrong-id-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals( 404, get.getResponse().getStatusCode());
        checkHasValidProblemJSON(get);
    }

    @Test
    public void getSwaggerUIWrongId() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH_UI + "/wrong-id-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals( 404, get.getResponse().getStatusCode());
        checkHasValidProblemJSON(get);
    }

    @Test
    public void getSwaggerUINoIdOld() throws Exception {
        get.getRequest().setUri(uiOld);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals( 404, get.getResponse().getStatusCode());
        checkHasValidProblemJSON(get);
    }

    @Test
    public void getSwaggerUINoId() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH_UI);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals( 404, get.getResponse().getStatusCode());
        checkHasValidProblemJSON(get);
    }

    private void checkHasValidProblemJSON(Exchange exc) throws IOException {
        assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
        assertTrue(exc.getResponse().isJSON());

        JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());

        assertTrue(json.has("title"));
        assertTrue(json.has("type"));
    }

    @Test
    public void getApiByIdOld() throws Exception {
        get.getRequest().setUri(metaOld + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals("application/x-yaml", get.getResponse().getHeader().getContentType());
        assertEquals("Nested Objects and Arrays Test API", getJsonFromYamlResponse(get).get("info").get("title").textValue());
    }

    @Test
    public void getApiById() throws Exception {
        get.getRequest().setUri(OpenAPIPublisherInterceptor.PATH + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals("application/x-yaml", get.getResponse().getHeader().getContentType());
        assertEquals("Nested Objects and Arrays Test API", getJsonFromYamlResponse(get).get("info").get("title").textValue());
    }

    private JsonNode getJsonFromYamlResponse(Exchange exc) throws IOException {
        return omYaml.readTree(exc.getResponse().getBody().getContent());
    }
}