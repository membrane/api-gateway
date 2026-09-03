/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.xmlprotection;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.protection.AbstractBodyProtectionInterceptor.OtherContentTypes.SKIP;
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionInterceptor.X_PROTECTION;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class XMLProtectionInterceptorTest {

    /**
     * Configuration is read in {@link XMLProtectionInterceptor#init()}, so every test builds and
     * initialises its own interceptor instead of reconfiguring a shared one.
     */
    private static XMLProtectionInterceptor interceptor(Consumer<XMLProtectionInterceptor> configuration) {
        XMLProtectionInterceptor interceptor = new XMLProtectionInterceptor();
        configuration.accept(interceptor);
        interceptor.init(new DefaultRouter());
        return interceptor;
    }

    private static XMLProtectionInterceptor interceptor() {
        return interceptor(i -> {
        });
    }

    private static Exchange xml(String body) throws Exception {
        return post("/").contentType(APPLICATION_XML).body(body).buildExchange();
    }

    private static Exchange xmlFrom(String resource) throws Exception {
        try (var is = XMLProtectionInterceptorTest.class.getResourceAsStream(resource)) {
            assertNotNull(is, "Test resource not found: " + resource);
            return post("/").contentType(APPLICATION_XML).body(is.readAllBytes()).buildExchange();
        }
    }

    private static String bodyOf(Exchange exc) {
        return exc.getResponse().getBodyAsStringDecoded();
    }

    @Test
    void wellformedXmlPasses() throws Exception {
        Exchange exc = xmlFrom("/customer.xml");
        assertEquals(CONTINUE, interceptor().handleRequest(exc));
    }

    @Test
    void emptyBodyIsNotScanned() throws Exception {
        Exchange exc = post("/").contentType(APPLICATION_XML).body("").buildExchange();
        assertEquals(CONTINUE, interceptor().handleRequest(exc));
    }

    @Test
    @DisplayName("A Content-Type other than XML is discarded")
    void nonXmlContentTypeIsDiscarded() throws Exception {
        Exchange exc = post("/").contentType(APPLICATION_JSON).body("{\"a\": 1}").buildExchange();

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(415, exc.getResponse().getStatusCode());
        assertTrue(bodyOf(exc).contains("is not XML"));
    }

    @Test
    @DisplayName("Malformed XML is a policy violation, reported with the parser's reason")
    void notWellformedIsRejected() throws Exception {
        Exchange exc = xmlFrom("/xml/not-wellformed.xml");

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertRejectedByPolicy(exc);
        assertTrue(bodyOf(exc).contains("Not well-formed XML"));
    }

    @Test
    @DisplayName("A malformed XML declaration hit while probing the encoding is a policy violation naming the encoding, not a server error")
    void malformedXmlDeclarationIsRejected() throws Exception {
        Exchange exc = post("/")
                .contentType(APPLICATION_XML) // no charset - forces the encoding probe to run
                .body("<?xml version=\"1.0\" encoding=\"not-a-real-charset\"?><foo/>")
                .buildExchange();

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertRejectedByPolicy(exc);
        assertTrue(bodyOf(exc).contains("invalid or unsupported encoding"), bodyOf(exc));
    }

    @Test
    @DisplayName("A body that cannot even be decoded is a server error, not a policy violation")
    void undecodableBodyIsReportedAsServerError() throws Exception {
        Exchange exc = post("/")
                .contentType(APPLICATION_XML)
                .body("<foo/>") // announced as gzip below, but is not
                .header("Content-Encoding", "gzip") // after body(), which clears Content-Encoding
                .buildExchange();

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
        assertNull(exc.getResponse().getHeader().getFirstValue(X_PROTECTION));
        // Outside production the cause is reported as the "reason" detail rather than a stacktrace
        assertTrue(bodyOf(exc).contains("GZIP"), bodyOf(exc));
    }

    @Test
    void removesDTD() throws Exception {
        Exchange exc = xml("""
                <?xml  version="1.0" encoding="ISO-8859-1"?>
                <!DOCTYPE foo [
                     <!ELEMENT foo ANY >
                   ]>
                <foo/>
                """);

        assertEquals(CONTINUE, interceptor().handleRequest(exc));

        // Should still contain the XML, but not the DTD
        assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("<foo"));
        assertFalse(exc.getRequest().getBodyAsStringDecoded().contains("DOCTYPE"));
    }

    @Test
    @DisplayName("Removing a DTD does not force UTF-8 decoding over the document's own declared encoding")
    void removesDTDPreservesDeclaredNonUtf8Encoding() throws Exception {
        String body = """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <!DOCTYPE foo [
                     <!ELEMENT foo ANY >
                   ]>
                <foo>café</foo>
                """;
        // No charset in Content-Type - the request declares its encoding only via the XML declaration
        Exchange exc = post("/").contentType(APPLICATION_XML).body(body.getBytes(ISO_8859_1)).buildExchange();

        assertEquals(CONTINUE, interceptor().handleRequest(exc));

        String result = new String(exc.getRequest().getBodyAsStreamDecoded().readAllBytes(), ISO_8859_1);
        assertTrue(result.contains("café"), result);
        assertFalse(result.contains("DOCTYPE"), result);
    }

    @Test
    @DisplayName("A rewritten document's XML declaration matches the charset it was actually written in, not the one the document declared")
    void rewrittenDeclarationMatchesActualCharsetNotDeclaredOne() throws Exception {
        // The HTTP charset (UTF-8) takes precedence over the document's own declared encoding
        // (ISO-8859-1) per resolveCharset() - the bytes below are UTF-8 despite what the declaration says.
        String body = """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <!DOCTYPE foo [
                     <!ELEMENT foo ANY >
                   ]>
                <foo>café</foo>
                """;
        Exchange exc = post("/")
                .contentType("application/xml; charset=UTF-8")
                .body(body.getBytes(UTF_8))
                .buildExchange();

        assertEquals(CONTINUE, interceptor().handleRequest(exc));

        String result = new String(exc.getRequest().getBodyAsStreamDecoded().readAllBytes(), UTF_8);
        assertTrue(result.contains("café"), result);
        assertTrue(result.contains("encoding=\"UTF-8\""), result);
        assertFalse(result.contains("ISO-8859-1"), result);
    }

    @Test
    @DisplayName("A document nothing was removed from is forwarded byte for byte")
    void acceptedDocumentIsNotRewritten() throws Exception {
        // Single quotes and the spacing survive only if the body is passed through rather than
        // re-serialised by the XMLEventWriter
        String body = "<?xml version='1.0'?><foo a='1'  b='2'><bar/></foo>";
        Exchange exc = xml(body);

        assertEquals(CONTINUE, interceptor().handleRequest(exc));
        assertEquals(body, exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void keepsDTDWhenRemovalIsSwitchedOff() throws Exception {
        Exchange exc = xml("""
                <?xml version="1.0"?>
                <!DOCTYPE foo [
                     <!ELEMENT foo ANY >
                   ]>
                <foo/>
                """);

        assertEquals(CONTINUE, interceptor(i -> i.setRemoveDTD(false)).handleRequest(exc));
        assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("DOCTYPE"));
    }

    @Test
    @DisplayName("An external entity is a security violation, not a gateway error - even while DTDs are being removed")
    void externalEntityIsRejected() throws Exception {
        Exchange exc = xmlFrom("/xml/entity-external.xml");

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertRejectedByPolicy(exc);
        assertTrue(bodyOf(exc).contains("External entity"));
    }

    @Test
    void tooLongElementNameIsRejected() throws Exception {
        Exchange exc = xmlFrom("/xml/long-element-name.xml");

        assertEquals(ABORT, interceptor(i -> i.setMaxElementNameLength(100)).handleRequest(exc));
        assertRejectedByPolicy(exc);
    }

    @Test
    void tooLongAttributeNameIsRejected() throws Exception {
        Exchange exc = xml("<foo %s=\"1\"/>".formatted("a".repeat(200)));

        assertEquals(ABORT, interceptor(i -> i.setMaxAttributeNameLength(100)).handleRequest(exc));
        assertRejectedByPolicy(exc);
        assertTrue(bodyOf(exc).contains("Attribute name of 200 characters"));
    }

    @Test
    void attributeCountAtLimitPasses() throws Exception {
        Exchange exc = xml("""
                <foo a="1" b="2" c="3" d="to much"/>""");

        assertEquals(CONTINUE, interceptor(i -> i.setMaxAttributeCount(4)).handleRequest(exc));
    }

    @Test
    void tooManyAttributesIsRejected() throws Exception {
        Exchange exc = xml("""
                <foo a="1" b="2" c="3" d="to much"/>""");

        assertEquals(ABORT, interceptor(i -> i.setMaxAttributeCount(3)).handleRequest(exc));
        assertRejectedByPolicy(exc);
    }

    @Test
    void nestingAtLimitPasses() throws Exception {
        assertEquals(CONTINUE, interceptor(i -> i.setMaxDepth(4)).handleRequest(xml(nested(4))));
    }

    @Test
    void tooDeeplyNestedIsRejected() throws Exception {
        Exchange exc = xml(nested(4));

        assertEquals(ABORT, interceptor(i -> i.setMaxDepth(3)).handleRequest(exc));
        assertRejectedByPolicy(exc);
        assertTrue(bodyOf(exc).contains("nesting depth"));
    }

    @Test
    void unlimitedDepthDisablesCheck() throws Exception {
        assertEquals(CONTINUE, interceptor(i -> i.setMaxDepth(-1)).handleRequest(xml(nested(2000))));
    }

    // --- Multipart / attachments -------------------------------------------------------------

    @Test
    void xmlPartOfMultipartIsInspected() throws Exception {
        Exchange exc = multipartExchange(part("data", APPLICATION_XML, "<averylongelementname/>"));

        assertEquals(ABORT, interceptor(i -> i.setMaxElementNameLength(5)).handleRequest(exc));
        assertRejectedByPolicy(exc);
        assertTrue(bodyOf(exc).contains("Element name"), bodyOf(exc));
        assertTrue(bodyOf(exc).contains("data"), "should name the offending part: " + bodyOf(exc));
    }

    @Test
    void benignMultipartPasses() throws Exception {
        Exchange exc = multipartExchange(part("data", APPLICATION_XML, "<foo/>"));

        assertEquals(CONTINUE, interceptor().handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void everyXmlPartIsInspected() throws Exception {
        Exchange exc = multipartExchange(
                part("first", APPLICATION_XML, "<foo/>"),
                part("second", APPLICATION_XML, "<averylongelementname/>"));

        assertEquals(ABORT, interceptor(i -> i.setMaxElementNameLength(5)).handleRequest(exc));
        assertTrue(bodyOf(exc).contains("second"), bodyOf(exc));
    }

    @Test
    void nonXmlPartIsRejectedByDefault() throws Exception {
        Exchange exc = multipartExchange(part("logo", "image/png", "PNG"));

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(415, exc.getResponse().getStatusCode());
        assertTrue(bodyOf(exc).contains("is not XML"), bodyOf(exc));
        assertTrue(bodyOf(exc).contains("logo"), "should name the offending part: " + bodyOf(exc));
    }

    @Test
    void nonXmlPartIsSkippedWhenConfigured() throws Exception {
        Exchange exc = multipartExchange(
                part("logo", "image/png", "PNG"),
                part("data", APPLICATION_XML, "<foo/>"));

        assertEquals(CONTINUE, interceptor(i -> i.setOtherContentTypes(SKIP)).handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    @DisplayName("A part without a Content-Type defaults to text/plain per RFC 2045 and is therefore not XML")
    void partWithoutContentTypeIsNotXml() throws Exception {
        Exchange exc = multipartExchange(part("data", null, "<foo/>"));

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(415, exc.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("A body without a Content-Type is still discarded, unlike in jsonProtection")
    void bodyWithoutContentTypeIsStillRejected() throws Exception {
        Exchange exc = post("/").body("<foo/>").buildExchange();
        exc.getRequest().getHeader().removeFields("Content-Type");

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(415, exc.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("A multipart body nothing was removed from reaches the backend as the client sent it")
    void multipartBodyIsNotModifiedWhenNothingIsRewritten() throws Exception {
        Exchange exc = multipartExchange(part("data", APPLICATION_XML, "<foo  a='1'/>"));
        byte[] before = exc.getRequest().getBodyAsStreamDecoded().readAllBytes();

        assertEquals(CONTINUE, interceptor().handleRequest(exc));
        assertArrayEquals(before, exc.getRequest().getBodyAsStreamDecoded().readAllBytes());
    }

    @Test
    @DisplayName("A DTD is taken out of an XML part and the rebuilt body keeps every other part intact")
    void dtdIsStrippedFromAnXmlPart() throws Exception {
        Exchange exc = multipartExchange(
                part("logo", "image/png", "PNG-not-really"),
                part("data", APPLICATION_XML, DTD_DOCUMENT));

        assertEquals(CONTINUE, interceptor(i -> i.setOtherContentTypes(SKIP)).handleRequest(exc));

        String rebuilt = exc.getRequest().getBodyAsStringDecoded();
        assertFalse(rebuilt.contains("DOCTYPE"), rebuilt);
        assertTrue(rebuilt.contains("<foo"), rebuilt);
        assertTrue(rebuilt.contains("PNG-not-really"), "the untouched part must survive: " + rebuilt);
        assertTrue(rebuilt.contains("name=\"logo\""), "part headers must survive: " + rebuilt);
        assertEquals(rebuilt.getBytes(UTF_8).length, exc.getRequest().getHeader().getContentLength());
    }

    /**
     * The rewritten copy of a part must not be able to introduce the message's own MIME boundary:
     * the character references below are inert on the wire but become a literal CRLF--boundary once
     * the protector re-serialises the document, splitting the body for the backend.
     */
    @Test
    void rewrittenPartCannotInjectAMultipartBoundary() throws Exception {
        Exchange exc = multipartExchange(part("data", APPLICATION_XML,
                "<?xml version=\"1.0\"?><!DOCTYPE foo [ <!ELEMENT foo ANY > ]><foo>&#xD;&#xA;--"
                + BOUNDARY + "--</foo>"));

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(bodyOf(exc).contains("MIME boundary"), bodyOf(exc));
    }

    /**
     * Part headers are copied through as bytes, so neither a raw UTF-8 filename (RFC 7578) nor a
     * folded continuation line is mangled or made to throw by the rebuild.
     */
    @Test
    void partHeadersSurviveTheRewriteByteForByte() throws Exception {
        String unusual = "Content-Disposition: form-data; name=\"logo\"; filename=\"Grüße.png\"\r\n"
                         + "X-Folded: first\r\n\tsecond\r\n"
                         + "Content-Type: image/png\r\n\r\nPNG";
        Exchange exc = multipartExchange(unusual, part("data", APPLICATION_XML, DTD_DOCUMENT));

        assertEquals(CONTINUE, interceptor(i -> i.setOtherContentTypes(SKIP)).handleRequest(exc));

        byte[] rebuilt = exc.getRequest().getBodyAsStreamDecoded().readAllBytes();
        assertTrue(indexOf(rebuilt, unusual.getBytes(UTF_8)) >= 0,
                "the part must be copied through byte for byte: " + new String(rebuilt, UTF_8));
    }

    @Test
    void nestedMultipartIsRejected() throws Exception {
        Exchange exc = multipartExchange(part("inner", "multipart/mixed; boundary=inner", "whatever"));

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(bodyOf(exc).contains("Nested multipart"), bodyOf(exc));
    }

    // --- Size limit --------------------------------------------------------------------------

    @Test
    void maxSizeRejectsAnOversizedDocument() throws Exception {
        Exchange exc = xml("<foo>" + "x".repeat(20000) + "</foo>");

        assertEquals(ABORT, interceptor(i -> i.setMaxSize(500)).handleRequest(exc));
        assertRejectedByPolicy(exc);
        assertTrue(bodyOf(exc).contains("maximum size"), bodyOf(exc));
    }

    @Test
    void documentAtTheSizeLimitPasses() throws Exception {
        assertEquals(CONTINUE, interceptor(i -> i.setMaxSize(5000)).handleRequest(xml("<foo/>")));
    }

    /** maxSize is per document, so an oversized part must be rejected without being buffered whole. */
    @Test
    void oversizedPartIsRejected() throws Exception {
        Exchange exc = multipartExchange(part("data", APPLICATION_XML, "<foo>" + "x".repeat(20000) + "</foo>"));

        assertEquals(ABORT, interceptor(i -> i.setMaxSize(500)).handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(bodyOf(exc).contains("maximum size"), bodyOf(exc));
        assertTrue(bodyOf(exc).contains("data"), bodyOf(exc));
    }

    /** Skipping a non-XML part needs its header only, so its body is never buffered. */
    @Test
    void oversizedNonXmlPartIsSkippedWithoutBuffering() throws Exception {
        Exchange exc = multipartExchange(part("logo", "image/png", "x".repeat(20000)));

        assertEquals(CONTINUE, interceptor(i -> {
            i.setOtherContentTypes(SKIP);
            i.setMaxSize(500);
        }).handleRequest(exc));
        assertNull(exc.getResponse());
    }

    // --- XOP / MTOM --------------------------------------------------------------------------

    /**
     * An XOP message is traversed as its raw parts rather than reassembled, so the root part is
     * inspected as the XML it declares itself to be and the binary attachments follow the
     * otherContentTypes policy.
     */
    @Test
    void xopRootPartIsInspected() throws Exception {
        assertEquals(ABORT, interceptor(i -> {
            i.setOtherContentTypes(SKIP);
            i.setMaxElementNameLength(3);
        }).handleRequest(xopExchange()));
    }

    @Test
    void xopAttachmentIsSkippedWhenConfigured() throws Exception {
        Exchange exc = xopExchange();

        assertEquals(CONTINUE, interceptor(i -> i.setOtherContentTypes(SKIP)).handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void xopAttachmentIsRejectedByDefault() throws Exception {
        Exchange exc = xopExchange();

        assertEquals(ABORT, interceptor().handleRequest(exc));
        assertEquals(415, exc.getResponse().getStatusCode());
    }

    private static Exchange xopExchange() throws Exception {
        try (var is = XMLProtectionInterceptorTest.class.getResourceAsStream("/multipart/embedded-byte-array.txt")) {
            assertNotNull(is, "Test resource not found");
            return post("/")
                    .contentType("multipart/related; type=\"application/xop+xml\"; "
                                 + "boundary=\"uuid:168683dc-43b3-4e71-8e66-efb633ef406b\"; "
                                 + "start=\"<root.message@cxf.apache.org>\"; start-info=\"text/xml\"")
                    .body(is.readAllBytes())
                    .buildExchange();
        }
    }

    private static final String BOUNDARY = "----MembraneTestBoundary";

    private static final String DTD_DOCUMENT =
            "<?xml version=\"1.0\"?><!DOCTYPE foo [ <!ELEMENT foo ANY > ]><foo/>";

    private static String part(String name, String contentType, String body) {
        return "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
               + (contentType == null ? "" : "Content-Type: " + contentType + "\r\n")
               + "\r\n" + body;
    }

    private static Exchange multipartExchange(String... parts) throws Exception {
        StringBuilder body = new StringBuilder();
        for (String part : parts)
            body.append("--").append(BOUNDARY).append("\r\n").append(part).append("\r\n");
        body.append("--").append(BOUNDARY).append("--\r\n");

        return post("/")
                .contentType("multipart/form-data; boundary=" + BOUNDARY)
                .body(body.toString().getBytes(UTF_8))
                .buildExchange();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++)
                if (haystack[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static void assertRejectedByPolicy(Exchange exc) {
        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals("Content violates XML security policy", exc.getResponse().getHeader().getFirstValue(X_PROTECTION));
    }

    private static String nested(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("<a>");
        for (int i = 0; i < depth; i++) sb.append("</a>");
        return sb.toString();
    }
}
