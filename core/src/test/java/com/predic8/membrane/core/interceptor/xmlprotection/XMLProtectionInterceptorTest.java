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
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionInterceptor.X_PROTECTION;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
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
