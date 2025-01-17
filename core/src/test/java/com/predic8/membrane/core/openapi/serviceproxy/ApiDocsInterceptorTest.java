/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.serviceproxy.ApiDocsInterceptor.getOpenAPIInterceptor;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiDocsInterceptorTest {

    private final ObjectMapper om = new ObjectMapper();

    Router router;
    Exchange exc = new Exchange(null);
    ApiDocsInterceptor interceptor;
    APIProxy rule;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        exc.setRequest(new Request.Builder().get("/foo").build());
        exc.setOriginalRequestUri("/foo");

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/fruitshop-api-v2-openapi-3.yml";
        rule = createProxy(router, spec);

        router.setExchangeStore(new ForgetfulExchangeStore());

        router.setTransport(new HttpTransport());
        router.add(rule);
        router.init();

        interceptor = new ApiDocsInterceptor();
        interceptor.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @AfterAll
    void tearDownAll() {
        router.shutdown();
    }

    @Test
    void initTest() {
        assertEquals(RETURN, interceptor.handleRequest(exc));
    }

    @Test
    void getOpenApiInterceptorTest() {
        assertEquals("openapi", getOpenAPIInterceptor(rule).get().getDisplayName());
        assertEquals(Optional.empty(), getOpenAPIInterceptor(new APIProxy()));
    }

    @Test
    void initializeRuleApiSpecsTest() {
        assertEquals(getOpenAPIInterceptor(rule).get().getApiProxy().apiRecords, interceptor.initializeRuleApiSpecs());
    }

    @Test
    void initializeEmptyRuleApiSpecsTest() {
        ApiDocsInterceptor adi = new ApiDocsInterceptor();
        adi.init(new Router());
        assertEquals(new HashMap<>(), adi.initializeRuleApiSpecs());
    }

    @Test
    void getHTMLOverview() {
        exc.getRequest().setUri("/api-docs");
        Header header = new Header();
        header.setAccept("html");
        exc.getRequest().setHeader(header);
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("<a href=\"/api-docs/ui/fruit-shop-api-v2-0-0\">Fruit Shop API</a>"));
    }

    @Test
    void getSwaggerUI() {
        exc.getRequest().setUri("/api-docs/ui/fruit-shop-api-v2-0-0");
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Swagger"));
    }

    @Test
    void getSwaggerUIWrongId() throws Exception {
        exc.getRequest().setUri("/api-docs/wrong-id");
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(404, exc.getResponse().getStatusCode());
        checkHasValidProblemJSON(exc);
    }

    @Test
    void noRewriterSetSpecRewritesTest() {
        interceptor.setSpecRewrites(rule);
        Rewrite rewrite = getRewrite();
        assertEquals(2000, rewrite.getPort());
        assertNull(rewrite.getHost());
    }

    @Test
    void rewriterSetSpecRewritesTest() {
        getOpenAPIInterceptor(rule).get().getApiProxy().getSpecs().getFirst().setRewrite(new Rewrite() {{
            setPort(3000);
            setHost("localhost");
            setBasePath("/foo");
        }});
        interceptor.setSpecRewrites(rule);
        Rewrite rewrite = getRewrite();
        assertEquals(3000, rewrite.getPort());
        assertEquals("/foo", rewrite.getBasePath());
        assertEquals("localhost", rewrite.getHost());
    }

    private Rewrite getRewrite() {
        return getOpenAPIInterceptor(rule).get().getApiProxy().getSpecs().getFirst().getRewrite();
    }

    private void checkHasValidProblemJSON(Exchange exc) throws IOException {
        assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
        assertTrue(exc.getResponse().isJSON());

        JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());

        assertTrue(json.has("title"));
        assertTrue(json.has("type"));
    }

    @Test
    void testSetPortIfNull() {
        Rewrite rewrite = new Rewrite();

        ApiDocsInterceptor.setIfNull(rewrite, Rewrite::getPort, Rewrite::setPort, 8080);
        assertEquals(8080, rewrite.getPort());

        ApiDocsInterceptor.setIfNull(rewrite, Rewrite::getPort, Rewrite::setPort, 9090);
        assertEquals(8080, rewrite.getPort());
    }

    @Test
    void testSetHostIfNull() {
        Rewrite rewrite = new Rewrite();

        ApiDocsInterceptor.setIfNull(rewrite, Rewrite::getHost, Rewrite::setHost, "localhost");
        assertEquals("localhost", rewrite.getHost());

        ApiDocsInterceptor.setIfNull(rewrite, Rewrite::getHost, Rewrite::setHost, "127.0.0.1");
        assertEquals("localhost", rewrite.getHost());
    }

    @Test
    void testSetBasePathIfNull() {
        Rewrite rewrite = new Rewrite();

        ApiDocsInterceptor.setIfNull(rewrite, Rewrite::getBasePath, Rewrite::setBasePath, "/api");
        assertEquals("/api", rewrite.getBasePath());

        ApiDocsInterceptor.setIfNull(rewrite, Rewrite::getBasePath, Rewrite::setBasePath, "/new-api");
        assertEquals("/api", rewrite.getBasePath());
    }
}