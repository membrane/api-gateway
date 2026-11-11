/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xml;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.get;
import static java.util.Locale.ROOT;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;


@SuppressWarnings({"UnnecessaryUnicodeEscape", "SpellCheckingInspection"})
public class Xml2JsonInterceptorTest {

    private static final Logger log = LoggerFactory.getLogger(Xml2JsonInterceptorTest.class);
    public static final String UMLAUTS = "\u00fc\u00f6\u00fc\u00f6\u00fc\u00f6";

    static Xml2JsonInterceptor interceptor;

    @BeforeAll
    static void setup() {
        interceptor = new Xml2JsonInterceptor();
        interceptor.init(new DefaultRouter());
    }

    @Test
    void invalidXml() {
        assertThrows(RuntimeException.class, () -> getJsonRootFromStream(processThroughInterceptor(fillAndGetExchange(
                new ByteArrayInputStream("<unclosed>".getBytes(UTF_8))))));
    }

    @Test
    void validTestxml2jsonWithEncodingInXml() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchange(loadResource("/xml/content-utf-8-encoding-utf-8.xml"))))
                        .get("bar").get("futf").asText());
    }

    @Test
    void validTestEncodingInHeader() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangeWithEncodingHeader(loadResource("/xml/content-utf-8-encoding-without.xml"), TEXT_XML_UTF8))).get("bar").get("futf").asText());
    }

    @Test
    void encodingClashTest() throws Exception {
        assertNotEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangeWithEncodingHeader(loadResource("/xml/content-utf-8-encoding-without.xml"),
                        TEXT_XML_ISO_8859_1))).get("bar").get("futf").asText());

    }

    @Test
    void isoEncoded() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(fillAndGetExchangeWithEncodingHeader(changeEncoding(loadResource("/xml/content-utf-8-encoding-without.xml"), UTF_8, ISO_8859_1),
                        TEXT_XML_ISO_8859_1))).get("bar").get("futf").asText());

    }

    @Test
    void iso88591EncodedWithoutEncodingInHTTPHeader() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptor(
                        fillAndGetExchangeWithEncodingHeader(loadResource("/xml/content-utf-8-encoding-iso-8859-1.xml"), TEXT_XML)))
                        .get("bar").get("futf").asText());
    }

    @Test
    void validTestxml2jsonResponse() throws Exception {
        assertEquals(UMLAUTS,
                getJsonRootFromStream(processThroughInterceptorResponse(loadResource("/xml/content-utf-8-encoding-utf-8.xml"))).get("bar").get("futf").asText());
    }

    @Test
    void statusCodeOfResponseError() throws URISyntaxException {
        var exc = get("/foo").buildExchange();
        exc.setResponse(Response.ok().contentType(TEXT_XML).body("<unclosed>").build());
        interceptor.handleResponse(exc);
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    /**
     * A byte order mark identifies the byte stream's encoding directly, so it must win over a
     * contradicting Content-Type charset here too - xml2Json used to take the declared charset
     * unconditionally and was the one XML consumer left out of that rule (issue #3279).
     */
    @Test
    void bomWinsOverAContradictingContentTypeCharset() throws Exception {
        byte[] utf16WithBom = ("<greeting>" + UMLAUTS + "</greeting>").getBytes(UTF_16);

        JsonNode root = getJsonRootFromStream(processThroughInterceptor(
                fillAndGetExchangeWithEncodingHeader(new ByteArrayInputStream(utf16WithBom),
                        "text/xml; charset=ISO-8859-1")));

        assertEquals(UMLAUTS, root.get("greeting").asText());
    }

    /**
     * Without a Content-Type charset the mark still decides, where the XML declaration alone
     * could not: the prolog of a UTF-16 document is not readable as ASCII.
     */
    @Test
    void bomDecidesWhenThereIsNoContentTypeCharset() throws Exception {
        byte[] utf16WithBom = ("<greeting>" + UMLAUTS + "</greeting>").getBytes(UTF_16);

        JsonNode root = getJsonRootFromStream(processThroughInterceptor(
                fillAndGetExchange(new ByteArrayInputStream(utf16WithBom))));

        assertEquals(UMLAUTS, root.get("greeting").asText());
    }

    private JsonNode getJsonRootFromStream(InputStream stream) throws IOException {
        return new ObjectMapper().readTree(stream);
    }

    private Request createRequestFromBytes(byte[] bytes) {
        return new Request.Builder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private Response createResponseFromBytes(byte[] bytes) {
        return new Response.ResponseBuilder().contentType(MimeType.TEXT_XML).body(bytes).build();
    }

    private InputStream processThroughInterceptor(Exchange exc) {
        Outcome outcome = interceptor.handleRequest(exc);
        if (outcome == Outcome.ABORT) {
            log.error("Interceptor aborted");
            log.error("Request: {}", exc.getRequest().getBodyAsStringDecoded());
            log.error("Response: {}", exc.getResponse().getBodyAsStringDecoded());
            throw new RuntimeException("Aborted");
        }
        return exc.getRequest().getBodyAsStream();
    }

    private InputStream processThroughInterceptorResponse(InputStream stream) throws Exception {
        Exchange exc = fillAndGetExchange(stream);
        interceptor.handleResponse(exc);
        return exc.getResponse().getBodyAsStream();
    }

    private InputStream loadResource(String path) {
        return this.getClass().getResourceAsStream(path);
    }

    private Exchange fillAndGetExchange(InputStream stream) throws IOException {
        return getExchangeFromStream(stream);
    }

    private Exchange fillAndGetExchangeWithEncodingHeader(InputStream stream, String contentTypeHeader) throws IOException {
        Exchange exc = getExchangeFromStream(stream);
        exc.getRequest().getHeader().setContentType(contentTypeHeader);
        exc.getResponse().getHeader().setContentType(contentTypeHeader);
        return exc;
    }

    private Exchange getExchangeFromStream(InputStream stream) throws IOException {
        Exchange exc = new Exchange(null);
        byte[] bytes = IOUtils.toByteArray(stream);
        exc.setRequest(createRequestFromBytes(bytes));
        exc.setResponse(createResponseFromBytes(bytes));
        return exc;
    }

    private static InputStream changeEncoding(InputStream in, Charset sourceEncoding, Charset targetEncoding) throws IOException {
        String text = new String(in.readAllBytes(), sourceEncoding);
        byte[] outBytes = text.getBytes(targetEncoding);
        return new ByteArrayInputStream(outBytes);
    }

    private static final String LIBRARY = """
            <library name="City">
                <book id="7">
                    <title>Dune</title>
                    <author>Frank Herbert</author>
                    <pages>412</pages>
                    <price currency="EUR">12.50</price>
                    <available>true</available>
                    <note xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
                </book>
            </library>""";

    private static final String ORDER = """
            <order xmlns="https://example.com/shop" xmlns:c="https://example.com/common">
                <id>42</id>
                <item><article>Coffee</article><quantity>2</quantity></item>
                <c:address><c:city>Bonn</c:city><c:zip>01234</c:zip></c:address>
            </order>""";

    static Xml2JsonInterceptor withSchema(String xsd) {
        final var interceptor = new Xml2JsonInterceptor();
        interceptor.setXmlSchema(xsd);
        interceptor.init(new DefaultRouter());
        return interceptor;
    }

    private static Exchange xmlRequest(String xml) throws Exception {
        return Request.post("/").contentType(APPLICATION_XML).body(xml).buildExchange();
    }

    @Test
    void anXmlSchemaTypesValuesAndMakesARepeatableElementAnArray() throws Exception {
        final var exc = xmlRequest(LIBRARY);

        assertEquals(Outcome.CONTINUE, withSchema("classpath:/xml-schema/library.xsd").handleRequest(exc));

        final var library = new ObjectMapper().readTree(exc.getRequest().getBodyAsStringDecoded()).get("library");
        assertEquals("City", library.get("@name").asText());
        final var book = library.get("book");
        assertTrue(book.isArray(), "maxOccurs=unbounded is an array even with one book: " + library);
        final var first = book.get(0);
        assertTrue(first.get("@id").isInt());
        assertEquals(7, first.get("@id").asInt());
        assertTrue(first.get("author").isArray());
        assertEquals("Frank Herbert", first.get("author").get(0).asText());
        assertTrue(first.get("pages").isNumber());
        assertEquals("EUR", first.get("price").get("@currency").asText());
        assertEquals(12.5, first.get("price").get("$value").asDouble());
        assertTrue(first.get("available").isBoolean());
        assertTrue(first.get("note").isNull());
        assertEquals(APPLICATION_JSON_UTF8, exc.getRequest().getHeader().getContentType());
    }

    @Test
    void anXmlSchemaWithIncludesAndImportsConvertsNamespacedDocuments() throws Exception {
        final var exc = xmlRequest(ORDER);

        assertEquals(Outcome.CONTINUE, withSchema("classpath:/xml-schema/shop.xsd").handleRequest(exc));

        final var order = new ObjectMapper().readTree(exc.getRequest().getBodyAsStringDecoded()).get("order");
        assertEquals(42, order.get("id").asInt());
        assertTrue(order.get("item").isArray());
        assertEquals(2, order.get("item").get(0).get("quantity").asInt());
        assertEquals("01234", order.get("address").get("zip").asText(), "an xs:string keeps its leading zero");
    }

    @Test
    void aDocumentElementTheXmlSchemaDoesNotDeclareIsA400InTheRequest() throws Exception {
        final var exc = xmlRequest("<magazine/>");

        assertEquals(Outcome.ABORT, withSchema("classpath:/xml-schema/library.xsd").handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("magazine"));
    }

    @Test
    void aDocumentElementTheXmlSchemaDoesNotDeclareIsA500InTheResponseThatHidesIt() throws Exception {
        final var exc = new Exchange(null);
        exc.setResponse(Response.ok().contentType(APPLICATION_XML).body("<magazine-at-db-host-3/>").build());

        final var interceptor = withSchema("classpath:/xml-schema/library.xsd");
        interceptor.getRouter().getConfiguration().setProduction(true);

        assertEquals(Outcome.ABORT, interceptor.handleResponse(exc));

        assertEquals(500, exc.getResponse().getStatusCode());
        assertFalse(exc.getResponse().getBodyAsStringDecoded().contains("db-host-3"),
                "what the backend sent must not reach the client");
    }

    @Test
    void malformedXmlWithAnXmlSchemaIsA400() throws Exception {
        final var exc = xmlRequest("<library>");

        assertEquals(Outcome.ABORT, withSchema("classpath:/xml-schema/library.xsd").handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void aHeuristicSettingCannotBeCombinedWithAnXmlSchema() {
        final var interceptor = new Xml2JsonInterceptor();
        interceptor.setXmlSchema("classpath:/xml-schema/library.xsd");
        interceptor.setForceList(List.of("book"));

        final var e = assertThrows(ConfigurationException.class, () -> interceptor.init(new DefaultRouter()));
        assertTrue(e.getMessage().contains("forceList"), e.getMessage());
    }

    @Test
    void anXmlSchemaThatCannotBeReadIsAConfigurationError() {
        assertThrows(ConfigurationException.class, () -> withSchema("classpath:/xml-schema/missing.xsd"));
    }

    private static Xml2JsonInterceptor heuristicInProduction() {
        final var interceptor = new Xml2JsonInterceptor();
        interceptor.init(new DefaultRouter());
        interceptor.getRouter().getConfiguration().setProduction(true);
        return interceptor;
    }

    @Test
    void malformedXmlFromTheClientIsAUserError() throws Exception {
        final var exc = Request.post("/").contentType(TEXT_XML).body("<unclosed>").buildExchange();

        assertEquals(Outcome.ABORT, heuristicInProduction().handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
        final var problem = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(problem.contains("<type>https://membrane-api.io/problems/user</type>"), problem);
    }

    @Test
    void malformedXmlFromTheBackendDoesNotReachTheClient() throws Exception {
        final var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().get("/").build());
        exc.setResponse(Response.ok().contentType("text/xml; charset=db-host-3").body("<unclosed-at-db-host-3>").build());

        assertEquals(Outcome.ABORT, heuristicInProduction().handleResponse(exc));

        assertEquals(500, exc.getResponse().getStatusCode());
        // Case-insensitive: the charset comes back upper-cased.
        assertFalse(exc.getResponse().getBodyAsStringDecoded().toLowerCase(ROOT).contains("db-host-3"),
                "what the backend sent must not reach the client: " + exc.getResponse().getBodyAsStringDecoded());
    }

    @Test
    void malformedXmlWithAnXmlSchemaKeepsItsExplanationInProduction() throws Exception {
        final var interceptor = withSchema("classpath:/xml-schema/library.xsd");
        interceptor.getRouter().getConfiguration().setProduction(true);
        final var exc = xmlRequest("<library>");

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));
        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("The XML request could not be converted to JSON."), body);
    }
}
