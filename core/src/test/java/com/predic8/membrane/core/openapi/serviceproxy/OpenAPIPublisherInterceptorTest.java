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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OpenAPIPublisherInterceptorTest {

    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();
    private final ObjectMapper om = new ObjectMapper();

    private static final String META_OLD = "/api-doc";
    private static final String UI_OLD = "/api-doc/ui";

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
    public void constructor() {
        assertTrue(interceptor.apis.size() >= 27);
        assertNotNull(interceptor.apis.get("references-test-v1-0"));
        assertNotNull(interceptor.apis.get("strings-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("extension-sample-v1-4"));
        assertNotNull(interceptor.apis.get("query-params-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("nested-objects-and-arrays-test-api-v1-0"));
        assertNotNull(interceptor.apis.get("references-response-test-v1-0"));
    }

    final List<String> uiParameters() {
        return new ArrayList<>() {{
            add(UI_OLD);
            add(OpenAPIPublisherInterceptor.PATH_UI);
        }};
    }

    final List<String> metaParameters() {
        return new ArrayList<>() {{
            add(META_OLD);
            add(OpenAPIPublisherInterceptor.PATH);
        }};
    }

    @ParameterizedTest
    @MethodSource("metaParameters")
    public void getApiDirectory(String testPath) throws Exception {
        get.getRequest().setUri(testPath);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(TestUtils.getMapFromResponse(get).size() >= 27);
    }

    @ParameterizedTest
    @MethodSource("metaParameters")
    public void getHTMLOverview(String testPath) throws Exception {
        get.getRequest().setUri(testPath);
        Header header = new Header();
        header.setAccept("html");
        get.getRequest().setHeader(header);
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("<a href=\"" + OpenAPIPublisherInterceptor.PATH_UI + "/servers-1-api-v1-0\">Servers 1 API</a>"));
    }

    @ParameterizedTest
    @MethodSource("uiParameters")
    public void getSwaggerUI(String testPath) throws Exception {
        get.getRequest().setUri(testPath + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertTrue(get.getResponse().getBodyAsStringDecoded().contains("html"));
    }

    @ParameterizedTest
    @MethodSource("uiParameters")
    public void getSwaggerUIWrongId(String testPath) throws Exception {
        get.getRequest().setUri(testPath + "/wrong-id-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals( 404, get.getResponse().getStatusCode());
        checkHasValidProblemJSON(get);
    }

    @ParameterizedTest
    @MethodSource("uiParameters")
    public void getSwaggerUINoId(String testPath) throws Exception {
        get.getRequest().setUri(testPath);
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

    @ParameterizedTest
    @MethodSource("metaParameters")
    public void getApiById(String testPath) throws Exception {
        get.getRequest().setUri(testPath + "/nested-objects-and-arrays-test-api-v1-0");
        assertEquals( RETURN, interceptor.handleRequest(get));
        assertEquals("application/x-yaml", get.getResponse().getHeader().getContentType());
        assertEquals("Nested Objects and Arrays Test API", getJsonFromYamlResponse(get).get("info").get("title").textValue());
    }

    private JsonNode getJsonFromYamlResponse(Exchange exc) throws IOException {
        return omYaml.readTree(exc.getResponse().getBody().getContent());
    }
}