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

package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.noContent;
import static com.predic8.membrane.core.openapi.util.Utils.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @Test
    void inputStreamToString() throws IOException {
        assertEquals("foo",Utils.inputStreamToString(Utils.stringToInputStream("foo")));
    }

    @Test
    void inputStreamToStringUmlauts() throws IOException {
        assertEquals("äöü",Utils.inputStreamToString(Utils.stringToInputStream("äöü")));
    }

    @Test
    void getRequestBodyFromRef() {
        assertEquals("CustomerRequest",Utils.getComponentLocalNameFromRef("#/components/requestBodies/CustomerRequest"));
    }

    @Test
    void getSchemaTypeFromRef() {
        assertEquals("Customer",Utils.getComponentLocalNameFromRef("#/components/schemas/Customer"));
    }

    @Test
    void getPathFromURLHostAndPath() throws URISyntaxException {
        assertEquals("/foo", getPathFromURL("http://localhost/foo"));
    }

    @Test
    void getPathFromURLSchemeHostPath() throws URISyntaxException {
        assertEquals("/foo", getPathFromURL("http://localhost/foo"));
    }

    @Test
    void getPathFromURLHostToplevelPath() throws URISyntaxException {
        assertEquals("/foo", getPathFromURL("http://localhost.de/foo"));
    }

    @Test
    void getPathFromURLHostToplevelPortPath() throws URISyntaxException {
        assertEquals("/foo", getPathFromURL("http://localhost.de:3000/foo"));
    }

    @Test
    void getPathFromURLHostToplevelPortPathComponents() throws URISyntaxException {
        assertEquals("/demo-api/v2/", getPathFromURL("http://localhost:3000/demo-api/v2/"));
    }

    @Test
    void getPathFromURLHostToplevelPortPathComponentsNoTrailingSlash() throws URISyntaxException {
        assertEquals("/demo-api/v2", getPathFromURL("http://localhost:3000/demo-api/v2"));
    }

    @Test
    void getPathFromURLHTTPS() throws URISyntaxException {
        assertEquals("/demo-api/v2", getPathFromURL("https://localhost:3000/demo-api/v2"));
    }

    @Test
    void getPathFromURLNoPath() throws URISyntaxException {
        assertEquals("", getPathFromURL("http://localhost:4567"));
    }

    private String getPathFromURL(String s) throws URISyntaxException {
        return UriUtil.getPathFromURL(new URIFactory(), s);
    }
    
    @Test
    void getMediaTypeFromContentTypeHeader() {
        assertEquals(APPLICATION_JSON,    Utils.getMediaTypeFromContentTypeHeader("application/json; charset=utf-8"));
        assertEquals(APPLICATION_JSON,    Utils.getMediaTypeFromContentTypeHeader(APPLICATION_JSON));
    }

    @Test
    void isIp() {
        assertTrue(Utils.isValidIp("10.0.0.0"));
        assertTrue(Utils.isValidIp("255.255.255.255"));
        assertFalse(Utils.isValidIp("255.255.255"));
        assertFalse(Utils.isValidIp("256.255.255.255"));
        assertFalse(Utils.isValidIp("255.255.-255.255"));
    }

    @Test
    void getTypeNameFromSchemaRef() {
        assertEquals("Customer", Utils.getComponentLocalNameFromRef("#/components/schemas/Customer"));
    }

    @Test
    void uuidInvalid() {
        assertFalse(isValidUUID(""));
        assertFalse(isValidUUID("9A991F7-0502-4E5E-83A2-F55B38E78192"));
        assertFalse(isValidUUID("9A991F71-0502-4E5E-83A2-F55B38E7819"));
    }

    @Test
    void uuidValid() {
        assertTrue(isValidUUID("9A991F71-0502-4E5E-83A2-F55B38E78192"));
    }

    @Test
    void emailInvalid() {
        assertFalse(isValidEMail("foo"));
        assertFalse(isValidEMail("foo.bar"));
        assertFalse(isValidEMail("foo@bar@baz"));
    }

    @Test
    void emailValid() {
        assertTrue(isValidEMail("nobody@predic8.de"));
    }

    @Test
    void uriValid() {
        assertTrue(isValidUri("http://www.ics.uci.edu/pub/ietf/uri/#Related"));
        assertTrue(isValidUri("urn:bar"));

        // From https://www.rfc-editor.org/rfc/rfc3986#section-1.1.2
        String[] samples = {
                "ftp://ftp.is.co.za/rfc/rfc1808.txt",
                "http://www.ietf.org/rfc/rfc2396.txt",
                "ldap://[2001:db8::7]/c=GB?objectClass?one",
                "mailto:John.Doe@example.com",
                "news:comp.infosystems.www.servers.unix",
                "tel:+1-816-555-1212",
                "telnet://192.0.2.16:80/",
                "urn:oasis:names:specification:docbook:dtd:xml:4.1.2"};

        for (String sample : samples) {
            assertTrue(isValidUri(sample));
        }
    }

    @Test
    void uriInvalid() {
        assertFalse(isValidUri("http"));
    }

    @Test
    void dateValid() {
        assertTrue(isValidDate("2022-11-19"));
        assertTrue(isValidDate("2022-12-31"));
    }

    @Test
    void dateInvalid() {
        assertFalse(isValidDate("2022-02-31"));
        assertFalse(isValidDate("2022-02-29"));
        assertFalse(isValidDate("2022-1-29"));
        assertFalse(isValidDate("2022-14-29"));
    }

    @Test
    void dateTimeValid() {
        assertTrue(isValidDateTime("2009-01-01T12:00:00+01:00"));
        assertTrue(isValidDateTime("2007-08-31T16:47+00:00"));
        assertTrue(isValidDateTime("2008-02-01T09:00:22"));
        assertTrue(isValidDateTime("2008-02-01T09:00"));
    }

    @Test
    void dateTimeInvalid() {
        assertFalse(isValidDateTime("2008-02-01"));
    }

    @Test
    void normalizeForId() {
        assertEquals("a-b", Utils.normalizeForId("a b"));
        assertEquals("a-b", Utils.normalizeForId("a%b"));
        assertEquals("a-3b-c12", Utils.normalizeForId("a-+# 3b C12"));
    }

    @Test
    void getOpenapiValidatorRequestFromExchange() throws IOException, ParseException {
        Exchange exc = new Exchange(null);
        Header header = new Header();
        header.setValue("X-Padding", "V0hQCMkJV4mKigp");
        header.setContentType("text/xml");
        exc.setOriginalRequestUri("/foo");
        exc.setRequest(new com.predic8.membrane.core.http.Request.Builder().method("POST").header(header).build());

        var request = Utils.getOpenapiValidatorRequest(exc);
        assertEquals("/foo",request.getPath());
        assertEquals("POST", request.getMethod());

        assertEquals(2,request.getHeaders().size());
        assertEquals("V0hQCMkJV4mKigp", request.getHeaders().get("X-Padding"));
        assertEquals("text/xml", request.getHeaders().get("Content-Type"));

        assertTrue(new ContentType("text/xml").match(request.getMediaType()));
    }

    @Test
    void getOpenapiValidatorResponse() throws IOException, ParseException {

        var json = new HashMap<String,Object>();
        json.put("foo",2);

        Exchange exc = new Exchange(null);
        exc.setResponse(Response.ok().body(json).build());

        var res = Utils.getOpenapiValidatorResponse(exc);
        assertEquals(200,res.getStatusCode());
        assertEquals("application/json",res.getHeaders().get("Content-Type"));
    }

    /**
     * Test that a response with no content type e.g. a 204 No Content works
     */
    @Test
    void getOpenapiValidatorResponseWithNoContentType() {
        Exchange exc = new Exchange(null);
        exc.setResponse(noContent().build());

        assertDoesNotThrow(() -> {
            assertNull(Utils.getOpenapiValidatorResponse(exc).getMediaType());
        });
    }

    @Test
    void getResourceAsStreamValidResource() throws IOException {
        assertEquals("baz",
                new String(requireNonNull(
                        getResourceAsStream(this, "/test/foo.bar")).readAllBytes(), StandardCharsets.UTF_8)
                );
    }

    @Test
    void getResourceAsStreamInvalidResource() {
        assertThrows(FileNotFoundException.class, () -> getResourceAsStream(this, "/doesnot.exist"));
    }

    @Test
    void testConvertToBigDecimal_withJsonNode() throws Exception {
        assertEquals(new BigDecimal("123.45"), convertToBigDecimal(new ObjectMapper().readTree("\"123.45\"")));
    }

    @Test
    void testConvertToBigDecimal_withString() {
        assertEquals(BigDecimal.valueOf(678.90), convertToBigDecimal("678.90"));
    }

    @Test
    void testConvertToBigDecimal_withInvalidString() {
        assertThrows(NumberFormatException.class, () -> convertToBigDecimal("invalid"));
    }
}