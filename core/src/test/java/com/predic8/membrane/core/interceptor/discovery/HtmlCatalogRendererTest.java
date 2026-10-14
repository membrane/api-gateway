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

    private final HtmlCatalogRenderer renderer = new HtmlCatalogRenderer(COLLECTION);

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
        assertFalse(html.contains("example.com"));
        assertFalse(html.contains("Identifier"));
        assertFalse(html.contains("Created"));
        assertFalse(html.contains("Modified"));
    }

    @Test
    void anEmptyCatalogSaysSo() {
        assertTrue(render(catalog()).contains("No APIs are published here."));
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

        assertTrue(html.contains("<code>/shop/v2/products</code>"));
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
        assertFalse(render(catalog(plainProxy())).contains("<details class=\"operations\""));
    }

    /**
     * Neither the summary nor the operationId of an operation is mandatory in OpenAPI, and neither
     * is a license's URL.
     */
    @Test
    void optionalOpenApiMembersMayAllBeMissing() {
        CatalogEntry sparse = CatalogEntry.builder()
                .aid("example.com:x").id("x").kind(API).name("X").description("X")
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
    void everyApiIsCollapsedUnderItsTitleAndLinkable() {
        String html = render(catalog(describedApi(), plainProxy()));

        assertTrue(html.contains("<details class=\"box api\" id=\"fruit-shop-api-v2-0-0\">\n<summary><h2>Fruit Shop API"));
        assertTrue(html.contains("<details class=\"box api\" id=\"shop\">\n<summary><h2>Shop"));
    }

    @Test
    void theHeaderListsNoIndexOfTheApis() {
        String html = render(catalog(describedApi(), plainProxy()));

        assertFalse(html.contains("<nav"));
        assertFalse(html.contains("href=\"#shop\""));
    }

    @Test
    void theApiCountIsBrokenDownByKindWhenKindsDiffer() {
        assertTrue(render(catalog(describedApi(), plainProxy())).contains("2 (1 api, 1 serviceProxy)"));
    }

    @Test
    void oneKindOfProxyIsCountedWithoutABreakdown() {
        String html = render(catalog(describedApi()));

        assertTrue(html.contains("<dd>1</dd>"));
        assertFalse(html.contains("(1 api)"));
    }

    @Test
    void operationsAreGroupedByTheirFirstTag() {
        CatalogEntry mixed = CatalogEntry.builder()
                .aid("example.com:x").id("x").kind(API).name("X").description("X")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .operations(List.of(
                        new CatalogOperation("GET", "/products", "getProducts", null, List.of("Products", "Catalog"), false),
                        new CatalogOperation("GET", "/health", "getHealth", null, List.of(), false)))
                .build();

        String html = render(catalog(mixed));

        assertTrue(html.contains("<th colspan=\"3\" scope=\"colgroup\">Products</th>"));
        assertTrue(html.contains("<th colspan=\"3\" scope=\"colgroup\">Other</th>"));
        // Listed under several tags, shown once, under the first.
        assertFalse(html.contains(">Catalog</th>"));
    }

    @Test
    void tagGroupsFollowTheOrderOfTheDocumentsTags() {
        final var entry = CatalogEntry.builder()
                .aid("example.com:x").id("x").kind(API).name("X").description("X")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .documentTags(List.of("Orders", "Products"))
                .operations(List.of(
                        new CatalogOperation("GET", "/health", "getHealth", null, List.of(), false),
                        new CatalogOperation("GET", "/products", "getProducts", null, List.of("Products"), false),
                        new CatalogOperation("GET", "/stock", "getStock", null, List.of("Stock"), false),
                        new CatalogOperation("GET", "/orders", "getOrders", null, List.of("Orders"), false)))
                .build();

        final var html = render(catalog(entry));

        final var orders = html.indexOf(">Orders</th>");
        final var products = html.indexOf(">Products</th>");
        final var stock = html.indexOf(">Stock</th>");
        final var other = html.indexOf(">Other</th>");
        assertTrue(orders != -1 && orders < products, "declared tags in declared order");
        assertTrue(products < stock, "undeclared tags after the declared ones");
        assertTrue(stock < other, "untagged operations last");
    }

    @Test
    void anOpenApiDescriptionIsShownAsItsFirstParagraphWithoutMarkdown() {
        final var entry = CatalogEntry.builder()
                .aid("example.com:x").id("x").kind(API).name("X")
                .description("![Logo](https://example.com/logo.png)\n\nShowcases [REST](https://example.com/rest) API\ndesign.\n\nMore details.")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .build();

        final var html = render(catalog(entry));

        assertTrue(html.contains("<p class=\"description\">Showcases REST API\ndesign.</p>"));
        assertFalse(html.contains("Logo"));
    }

    @Test
    void furtherParagraphsOfADescriptionAreCollapsedBehindMore() {
        final var html = render(catalog(describedWith("First.\n\nSecond.\n\nThird.")));

        assertTrue(html.contains("""
                <p class="description">First.</p>
                <details class="more">
                <summary>More</summary>
                <p class="description">Second.</p>
                <p class="description">Third.</p>
                </details>
                """));
    }

    @Test
    void aOneParagraphDescriptionNeedsNoMore() {
        assertFalse(render(catalog(describedWith("Only this."))).contains("class=\"more\""));
    }

    private static CatalogEntry describedWith(String description) {
        return CatalogEntry.builder()
                .aid("example.com:x").id("x").kind(API).name("X").description(description)
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .build();
    }

    @Test
    void thePageLinksToTheJson() {
        assertTrue(render(catalog()).contains("<a href=\"?format=json\">View as JSON</a>"));
    }

    @Test
    void anOperationOfAnApiAtTheRootKeepsItsPath() {
        assertTrue(render(catalog(withOperations("/", "https://example.com/api-docs/ui/x",
                new CatalogOperation("GET", "/things", null, null, List.of(), false))))
                .contains("<code>/things</code>"));
    }

    @Test
    void operationsLinkToTheirPlaceInSwaggerUi() {
        assertTrue(render(catalog(describedApi())).contains(
                "<a href=\"https://example.com/api-docs/ui/fruit-shop-api-v2-0-0#/Products/getProducts\"><code>/shop/v2/products</code></a>"));
    }

    /**
     * Swagger UI lists untagged operations under <code>default</code> and expects spaces in the
     * fragment as <code>%20</code>.
     */
    @Test
    void swaggerUiLinksFollowItsDeepLinkFormat() {
        final var html = render(catalog(withOperations("/", "https://example.com/ui",
                new CatalogOperation("GET", "/health", "getHealth", null, List.of(), false),
                new CatalogOperation("GET", "/stock", "get Stock", null, List.of("Stock Levels"), false))));

        assertTrue(html.contains("href=\"https://example.com/ui#/default/getHealth\""));
        assertTrue(html.contains("href=\"https://example.com/ui#/Stock%20Levels/get%20Stock\""));
    }

    @Test
    void anOperationSwaggerUiCannotAddressIsNotLinked() {
        final var html = render(catalog(withOperations("/", "https://example.com/ui",
                new CatalogOperation("GET", "/anonymous", null, null, List.of(), false),
                new CatalogOperation("GET", "/slashed", "getSlashed", null, List.of("a/b"), false))));

        assertFalse(html.contains("https://example.com/ui#"));
    }

    @Test
    void withoutSwaggerUiOperationsAreNotLinked() {
        assertTrue(render(catalog(withOperations("/", null,
                new CatalogOperation("GET", "/things", "getThings", null, List.of(), false))))
                .contains("<td><code>/things</code></td>"));
    }

    private static CatalogEntry withOperations(String path, String swaggerUi, CatalogOperation... operations) {
        return CatalogEntry.builder()
                .aid("example.com:x").id("x").kind(API).name("X").description("X")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, path, "*", "http://example.com" + path))
                .links(new CatalogEntry.Links(swaggerUi, null))
                .operations(List.of(operations))
                .build();
    }

    @Test
    void operationsWithoutTagsStayOneFlatTable() {
        CatalogEntry untagged = CatalogEntry.builder()
                .aid("example.com:x").id("x").kind(API).name("X").description("X")
                .endpoint(new CatalogEntry.Endpoint("http", "example.com", 80, "/", "*", "http://example.com/"))
                .operations(List.of(new CatalogOperation("GET", "/things", "getThings", null, List.of(), false)))
                .build();

        assertFalse(render(catalog(untagged)).contains("class=\"group\""));
    }

    @Test
    void aDeprecatedOperationSaysSoInWords() {
        assertTrue(render(catalog(describedApi())).contains("<span class=\"badge\">deprecated</span>"));
    }

    @Test
    void theOperationIdStaysVisibleNextToTheSummary() {
        String html = render(catalog(describedApi()));

        assertTrue(html.contains("Get all products <span class=\"opid\">getProducts</span>"));
    }

    @Test
    void aSoapProxyLinksItsWsdl() {
        CatalogEntry soap = CatalogEntry.builder()
                .aid("example.com:articles").id("articles").kind(SOAP_PROXY).name("ArticleService").description("ArticleService")
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
                .aid("example.com:x").id("x").kind(API).name("<script>alert(1)</script>").description("a & b")
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
                .aid("example.com:x").id("x").kind(API).name("X").description("X")
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
