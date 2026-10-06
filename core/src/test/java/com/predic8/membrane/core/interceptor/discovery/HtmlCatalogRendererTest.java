/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.discovery;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.ProxyKind.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogTestFixtures.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class HtmlCatalogRendererTest {

    private final HtmlCatalogRenderer renderer = new HtmlCatalogRenderer();

    private String render(Catalog catalog) {
        return new String(renderer.render(catalog), UTF_8);
    }

    @Test
    void isServedAsHtmlAndRendersOperations() {
        assertEquals(TEXT_HTML_UTF8, renderer.contentType());
        assertTrue(renderer.rendersOperations());
    }

    @Test
    void theCollectionIsTheHeadingOfThePage() {
        String html = render(catalog());

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<title>Gateway APIs</title>"));
        assertTrue(html.contains("<h1>Gateway APIs</h1>"));
        assertTrue(html.contains("Every API of this gateway"));
        assertTrue(html.contains("example.com:gateway"));
        assertTrue(html.contains("2026-01-15"));
    }

    @Test
    void anEmptyCatalogSaysSo() {
        assertTrue(render(catalog()).contains("No APIs are deployed on this gateway."));
    }

    @Test
    void aDescribedApiShowsEverythingTheGatewayKnows() {
        String html = render(catalog(describedApi()));

        assertTrue(html.contains("<h2>Fruit Shop API"));
        assertTrue(html.contains("2.0.0"));
        assertTrue(html.contains("Showcases REST API design"));
        assertTrue(html.contains("<a href=\"https://example.com/shop/v2/\">https://example.com/shop/v2/</a>"));
        assertTrue(html.contains("href=\"https://example.com/api-docs/ui/fruit-shop-api-v2-0-0\""));
        assertTrue(html.contains("href=\"https://example.com/api-docs/fruit-shop-api-v2-0-0\""));
        assertTrue(html.contains("Products"));
        assertTrue(html.contains("href=\"mailto:info@predic8.de\""));
        assertTrue(html.contains("href=\"https://www.apache.org/licenses/LICENSE-2.0\""));
        assertTrue(html.contains("href=\"https://example.com/terms\""));
    }

    @Test
    void operationsAreListedWithMethodPathAndSummary() {
        String html = render(catalog(describedApi()));

        assertTrue(html.contains("<code>/products</code>"));
        assertTrue(html.contains("Get all products"));
        assertTrue(html.contains("class=\"method get\">GET<"));
        assertTrue(html.contains("<tr class=\"deprecated\">"));
    }

    /**
     * A catalog lists every API of the gateway, so the operations of each one start collapsed and
     * the reader opens the ones they care about.
     */
    @Test
    void operationsCollapseIntoADetailsBlockThatStartsClosed() {
        String html = render(catalog(describedApi()));

        assertTrue(html.contains("<details class=\"operations\">"));
        assertTrue(html.contains("<summary>Operations <span class=\"count\">2</span></summary>"));
        assertFalse(html.contains("<details class=\"operations\" open"));
    }

    @Test
    void anApiWithoutOperationsGetsNoDetailsBlock() {
        assertFalse(render(catalog(plainProxy())).contains("<details"));
    }

    /**
     * Neither the summary nor the operationId of an operation is mandatory in OpenAPI, and neither
     * is a license's URL.
     */
    @Test
    void optionalOpenApiMembersMayAllBeMissing() {
        CatalogEntry sparse = CatalogEntry.builder()
                .aid("example.com:x").kind(API).name("X").description("X")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .license(new CatalogEntry.License(null, null))
                .operations(List.of(new CatalogOperation("GET", "/things", null, null, List.of(), false)))
                .build();

        assertTrue(render(catalog(sparse)).contains("<code>/things</code>"));
    }

    @Test
    void aPlainProxyGetsNoDeadLinksAndNoWildcardMethod() {
        String html = render(catalog(plainProxy()));

        assertTrue(html.contains("<h2>Shop"));
        assertTrue(html.contains("serviceProxy"));
        assertFalse(html.contains("Swagger UI"));
        assertFalse(html.contains("<table"));
        assertFalse(html.contains(">*<"));
    }

    @Test
    void aSoapProxyLinksItsWsdl() {
        CatalogEntry soap = CatalogEntry.builder()
                .aid("example.com:articles").kind(SOAP_PROXY).name("ArticleService").description("ArticleService")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 2000, "/articles", "*", "http://example.com:2000/articles"))
                .soap(new CatalogEntry.SoapInfo("http://example.com:2000/articles?wsdl", "ArticleService", "ArticleServicePort"))
                .build();

        assertTrue(render(catalog(soap)).contains("href=\"http://example.com:2000/articles?wsdl\""));
    }

    /**
     * Titles, descriptions and contact details come from OpenAPI documents, which are not always
     * written by whoever runs the gateway.
     */
    @Test
    void textFromOpenApiDocumentsIsEscaped() {
        CatalogEntry hostile = CatalogEntry.builder()
                .aid("example.com:x").kind(API).name("<script>alert(1)</script>").description("a & b")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .tags(List.of("<b>tag</b>"))
                .build();

        String html = render(catalog(hostile));

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("a &amp; b"));
        assertFalse(html.contains("<b>tag</b>"));
    }

    @Test
    void onlyHttpUrlsBecomeLinks() {
        CatalogEntry hostile = CatalogEntry.builder()
                .aid("example.com:x").kind(API).name("X").description("X")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .contact(new CatalogEntry.Contact("Evil", null, "javascript:alert(1)"))
                .termsOfService("JavaScript:alert(2)")
                .build();

        String html = render(catalog(hostile));

        assertFalse(html.contains("href=\"javascript:"));
        assertFalse(html.contains("href=\"JavaScript:"));
        assertTrue(html.contains("javascript:alert(1)"));
    }
}
