/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
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
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

class RewriteTest {

    Rewrite rewriteAll = new Rewrite();
    Rewrite rewriteNothing = new Rewrite();

    OpenAPISpec rewritesAllSpec;

    Map<String, OpenAPIRecord> records;

    Exchange get = new Exchange(null);

    @BeforeEach
    void setUp() throws IOException {
        rewriteAll.host = "predic8.de";
        rewriteAll.port = 8080;
        rewriteAll.protocol = "https";

        Router router = new Router();
        router.setUriFactory(new URIFactory());
        router.setBaseLocation("");

        OpenAPIRecordFactory openAPIRecordFactory = new OpenAPIRecordFactory(router);

        rewritesAllSpec = getSpecRewriteAll();

        OpenAPISpec spec = new OpenAPISpec();
        spec.setDir("src/test/resources/openapi/specs");
        records = openAPIRecordFactory.create(singletonList(spec));

        get.setRequest(new Request.Builder().method("GET").build());
        get.setRule(new NullRule());
        get.setOriginalHostHeader("api.predic8.de:80");
    }

    @NotNull
    private static OpenAPISpec getSpecRewriteAll() {
        OpenAPISpec spec = new OpenAPISpec();
        spec.setLocation("src/test/resources/openapi/specs/info-servers.yml");
        Rewrite rewrite = new Rewrite();
        rewrite.setProtocol("https");
        rewrite.setPort(443);
        rewrite.setHost("membrane-api.io");
        spec.setRewrite(rewrite);
        return spec;
    }

    @Test
    void rewriteProtocol() {
        assertEquals("https", rewriteAll.rewriteProtocol("http"));
        assertEquals("http", rewriteNothing.rewriteProtocol("http"));
    }

    @Test
    void rewriteHost() {
        assertEquals("predic8.de", rewriteAll.rewriteHost("membrane-api.io"));
        assertEquals("membrane-api.io", rewriteNothing.rewriteHost("membrane-api.io"));
    }

    @Test
    void rewritePort() {
        assertEquals("8080", rewriteAll.rewritePort("80"));
        assertEquals("80", rewriteNothing.rewritePort("80"));
    }

    /**
     * Test if rewritting changes the OpenAPI document.
     * Secures currency.
     */
    @Test
    void rewritingDoesNotChangeTheSpec() throws Exception {
        OpenAPIRecord rec = records.get("servers-1-api-v1-0");
        JsonNode servers = rec.node.get("servers");

        String urlBefore = servers.get(0).get("url").asText();

        rec.rewriteOpenAPI(get, new URIFactory());

        JsonNode servers1 = rec.node.get("servers");
        assertEquals(urlBefore, servers1.get(0).get("url").asText());
    }

    @Test
    void rewriteSwagger2AccordingToRequestTest() throws Exception {
        assertEquals("api.predic8.de:80", records.get("fruit-shop-api-swagger-2-v1-0-0").rewriteOpenAPI(get, new URIFactory()).get("host").asText());
    }

    @Test
    void rewriteOpenAPIAccordingToRequestTest() throws Exception {
        JsonNode servers = records.get("servers-1-api-v1-0").rewriteOpenAPI(get, new URIFactory()).get("servers");
        assertEquals(1,servers.size());
        assertEquals("http://api.predic8.de/base/v2", servers.get(0).get("url").asText());
        assertEquals("Test System", records.get("servers-1-api-v1-0").node.get("servers").get(0).get("description").asText());
    }

    @Test
    void rewriteOpenAPIAccordingToRequest3Servers() throws Exception {
        OpenAPIRecord rec = records.get("servers-3-api-v1-0");
        JsonNode servers = rec.rewriteOpenAPI(get, new URIFactory()).get("servers");
        assertEquals(3,servers.size());
        assertEquals("http://api.predic8.de/foo",servers.get(0).get("url").asText());
        assertEquals("http://api.predic8.de/foo",servers.get(1).get("url").asText());
        assertEquals("http://api.predic8.de/foo",servers.get(2).get("url").asText());
    }

    @Test
    void rewriteOpenAPIAccordingToRewrite3Servers() throws Exception {
        OpenAPIRecord rec = records.get("servers-3-api-v1-0");
        rec.spec.rewrite.setHost("membrane-api.do");
        rec.spec.rewrite.setPort(8443);
        rec.spec.rewrite.setProtocol("https");
        JsonNode servers = rec.rewriteOpenAPI(get, new URIFactory()).get("servers");
        assertEquals(3,servers.size());
        assertEquals("https://membrane-api.do:8443/foo",servers.get(0).get("url").asText());
        assertEquals("https://membrane-api.do:8443/foo",servers.get(1).get("url").asText());
        assertEquals("https://membrane-api.do:8443/foo",servers.get(2).get("url").asText());
    }

    @Test
    void rewriteRequestHostHeaderWithoutPort() throws Exception {
        OpenAPIRecord rec = records.get("servers-3-api-v1-0");
        get.setOriginalHostHeader("api.predic8.de");
        JsonNode servers = rec.rewriteOpenAPI(get, new URIFactory()).get("servers");
        assertEquals("http://api.predic8.de/foo", servers.get(0).get("url").textValue());
        assertEquals("http://api.predic8.de/foo", servers.get(1).get("url").textValue());
        assertEquals("http://api.predic8.de/foo", servers.get(2).get("url").textValue());
    }

    @Test
    void rewriteUrl() throws URISyntaxException {
        assertEquals("http://api.predic8.de/foo", new Rewrite().rewriteUrl(get,"http://localhost:3000/foo", new URIFactory()));
        assertEquals("http://api.predic8.de/foo", new Rewrite().rewriteUrl(get,"http://localhost/foo", new URIFactory()));
    }

    @Test
    void rewriteOpenAPI3WithNoServers() throws Exception {
        assertTrue(records.get("no-servers-v1-0").rewriteOpenAPI(get, new URIFactory()).get("servers").isEmpty());
    }
}