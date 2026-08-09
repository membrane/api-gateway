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
package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.Header.CONTENT_ENCODING;
import static com.predic8.membrane.core.http.Header.TRANSFER_ENCODING;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.xml.XMLConstants.NULL_NS_URI;
import static javax.xml.xpath.XPathConstants.STRING;
import static org.junit.jupiter.api.Assertions.*;

class XmlDomBodyTest {

    private static final String SOAP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body><p:order xmlns:p="http://example.com/p"><p:id>  spaced  </p:id></p:order></s:Body>
            </s:Envelope>""";

    private static final String LATIN1_XML = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <greeting>Grüße</greeting>""";

    private static Request requestWith(byte[] content) {
        Request req = new Request();
        req.setBodyContent(content);
        return req;
    }

    @Test
    void readPathLeavesBytesAndContentLengthUntouched() {
        byte[] original = SOAP.getBytes(UTF_8);
        Request req = requestWith(original);

        assertNotNull(XmlDomBody.documentOf(req));

        assertArrayEquals(original, req.getBody().getContent());
        assertEquals(original.length, req.getHeader().getContentLength());
        assertInstanceOf(XmlDomBody.class, req.getBody());
    }

    @Test
    void documentIsSharedBetweenConsumers() {
        Request req = requestWith(SOAP.getBytes(UTF_8));

        assertSame(XmlDomBody.documentOf(req), XmlDomBody.documentOf(req));
    }

    @Test
    void replaceBodyDoesNotReformat() {
        Request req = requestWith(SOAP.getBytes(UTF_8));

        XmlDomBody.replaceBody(req, XmlDomBody.documentOf(req));

        String serialized = req.getBodyAsStringDecoded();
        assertTrue(serialized.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><s:Envelope"), serialized);
        // No whitespace inserted into elements: the signed-content invariant.
        assertTrue(serialized.contains("<s:Body><p:order xmlns:p=\"http://example.com/p\"><p:id>  spaced  </p:id></p:order></s:Body>"), serialized);
        // The original prefix bindings survive.
        assertTrue(serialized.contains("xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""), serialized);
    }

    @Test
    void replaceBodyPublishesMutationAndAdjustsHeader() {
        Request req = requestWith(SOAP.getBytes(UTF_8));
        req.getHeader().setValue(TRANSFER_ENCODING, "chunked");
        Document doc = XmlDomBody.documentOf(req);

        Element added = doc.createElementNS("http://example.com/p", "p:note");
        added.setTextContent("hi");
        doc.getDocumentElement().appendChild(added);
        XmlDomBody.replaceBody(req, doc);

        assertTrue(req.getBodyAsStringDecoded().contains("<p:note"));
        assertEquals(req.getBody().getLength(), req.getHeader().getContentLength());
        assertNull(req.getHeader().getFirstValue(TRANSFER_ENCODING));
        assertNull(req.getHeader().getFirstValue(CONTENT_ENCODING));
    }

    /**
     * A document that declared an encoding is written back in it, so a header claiming a different
     * one has to be corrected to match: RFC 7303 gives the Content-Type charset precedence over the
     * declaration, so leaving it would have a receiver misdecode the body - and a WS-Security peer
     * digest bytes other than the signed ones.
     */
    @Test
    void replaceBodyCorrectsAContentTypeCharsetThatContradictsTheBytes() {
        Request req = requestWith(LATIN1_XML.getBytes(ISO_8859_1));
        req.getHeader().setContentType("text/xml; charset=UTF-8");

        XmlDomBody.replaceBody(req, XmlDomBody.documentOf(req));

        assertEquals("ISO-8859-1", req.getHeader().getCharset());
        assertTrue(req.getHeader().getContentType().startsWith("text/xml"), req.getHeader().getContentType());
        // Read back as the corrected header now instructs: the character survives.
        assertTrue(new String(req.getBody().getContent(), ISO_8859_1).contains("Grüße"),
                new String(req.getBody().getContent(), ISO_8859_1));
    }

    /**
     * The mirror image: a document with no declared encoding comes out UTF-8, so a header left
     * saying ISO-8859-1 is the one that is wrong.
     */
    @Test
    void replaceBodyCorrectsTheCharsetOfAnUndeclaredDocumentToUtf8() {
        Request req = requestWith("<greeting>Grüße</greeting>".getBytes(UTF_8));
        req.getHeader().setContentType("text/xml; charset=ISO-8859-1");

        XmlDomBody.replaceBody(req, XmlDomBody.documentOf(req));

        assertEquals("UTF-8", req.getHeader().getCharset());
        assertTrue(new String(req.getBody().getContent(), UTF_8).contains("Grüße"),
                new String(req.getBody().getContent(), UTF_8));
    }

    /**
     * A charset the sender omitted stays omitted: with no parameter the XML declaration decides, so
     * the two cannot contradict each other and there is nothing to correct.
     */
    @Test
    void replaceBodyDoesNotAddACharsetThatWasNotThere() {
        Request req = requestWith(LATIN1_XML.getBytes(ISO_8859_1));
        req.getHeader().setContentType("text/xml");

        XmlDomBody.replaceBody(req, XmlDomBody.documentOf(req));

        assertEquals("text/xml", req.getHeader().getContentType());
        assertNull(req.getHeader().getCharset());
    }

    /**
     * An agreeing charset is left byte-identical rather than rewritten through the Content-Type
     * parser, which would reorder or requote parameters for no reason.
     */
    @Test
    void replaceBodyLeavesAnAgreeingCharsetUntouched() {
        Request req = requestWith(LATIN1_XML.getBytes(ISO_8859_1));
        req.getHeader().setContentType("text/xml; charset=iso-8859-1");

        XmlDomBody.replaceBody(req, XmlDomBody.documentOf(req));

        assertEquals("text/xml; charset=iso-8859-1", req.getHeader().getContentType());
    }

    @Test
    void replaceBodyLeavesAnUnparseableContentTypeAlone() {
        Request req = requestWith(SOAP.getBytes(UTF_8));
        req.getHeader().setContentType("not a media type at all");

        XmlDomBody.replaceBody(req, XmlDomBody.documentOf(req));

        assertEquals("not a media type at all", req.getHeader().getContentType());
    }

    @Test
    void byteLevelWriteDiscardsTheDocument() {
        Request req = requestWith(SOAP.getBytes(UTF_8));
        Document first = XmlDomBody.documentOf(req);

        req.setBodyContent(SOAP.replace("spaced", "rewritten").getBytes(UTF_8));

        Document second = XmlDomBody.documentOf(req);
        assertNotSame(first, second);
        assertTrue(second.getDocumentElement().getTextContent().contains("rewritten"));
    }

    /**
     * The document is parsed from the decoded stream, but the body keeps the compressed bytes it
     * arrived with, so Content-Encoding and Content-Length still describe it.
     */
    @Test
    void gzippedBodyIsCachedWithoutDecodingTheMessage() throws IOException {
        byte[] compressed = gzip(SOAP.getBytes(UTF_8));
        Request req = requestWith(compressed);
        req.getHeader().setValue(CONTENT_ENCODING, "gzip");

        Document doc = XmlDomBody.documentOf(req);

        assertEquals("Envelope", doc.getDocumentElement().getLocalName());
        assertInstanceOf(XmlDomBody.class, req.getBody());
        assertArrayEquals(compressed, req.getBody().getContent(), "must be forwarded as received");
        assertEquals("gzip", req.getHeader().getFirstValue(CONTENT_ENCODING));
        assertEquals(compressed.length, req.getHeader().getContentLength());
        assertSame(doc, XmlDomBody.documentOf(req), "the decode must not be repeated");
    }

    /**
     * A multipart body may be an XOP package that {@link Message#getBodyAsStreamDecoded()}
     * reassembles into a plain envelope. The reassembled envelope is what gets parsed, but the
     * multipart bytes — and with them the attachments — stay on the message.
     */
    @Test
    void multipartBodyIsCachedWithoutInliningTheAttachments() {
        byte[] original = SOAP.getBytes(UTF_8);
        Request req = requestWith(original);
        req.getHeader().setContentType("multipart/related; type=\"application/xop+xml\"; boundary=b; start=\"<s>\"");

        assertNotNull(XmlDomBody.documentOf(req));

        assertArrayEquals(original, req.getBody().getContent(), "must be forwarded as received");
        assertTrue(req.getHeader().isMultipart());
    }

    /**
     * A reader borrows the document: what it does to the tree stays unpublished until it says so,
     * so the message still forwards the bytes it arrived with.
     */
    @Test
    void readDoesNotPublish() {
        byte[] original = SOAP.getBytes(UTF_8);
        Request req = requestWith(original);

        String name = XmlDomBody.read(req, doc -> {
            doc.getDocumentElement().setAttribute("unpublished", "yes");
            return doc.getDocumentElement().getLocalName();
        });

        assertEquals("Envelope", name);
        assertArrayEquals(original, req.getBody().getContent());
    }

    @Test
    void modifyPublishesTheMutation() {
        Request req = requestWith(SOAP.getBytes(UTF_8));

        XmlDomBody.modify(req, doc -> {
            Element added = doc.createElementNS("http://example.com/p", "p:note");
            added.setTextContent("hi");
            doc.getDocumentElement().appendChild(added);
        });

        assertTrue(req.getBodyAsStringDecoded().contains(">hi</p:note>"), req.getBodyAsStringDecoded());
        assertEquals(req.getBody().getLength(), req.getHeader().getContentLength());
    }

    /**
     * The mutation works on the shared document, so a consumer that parsed before it ran sees the
     * change rather than a detached copy.
     */
    @Test
    void modifyMutatesTheSharedDocument() {
        Request req = requestWith(SOAP.getBytes(UTF_8));
        Document before = XmlDomBody.documentOf(req);

        XmlDomBody.modify(req, doc -> doc.getDocumentElement().setAttribute("marked", "yes"));

        assertEquals("yes", before.getDocumentElement().getAttribute("marked"));
    }

    @Test
    void modifyLeavesTheBodyAloneWhenTheMutationThrows() {
        byte[] original = SOAP.getBytes(UTF_8);
        Request req = requestWith(original);

        assertThrows(IllegalStateException.class, () -> XmlDomBody.modify(req, doc -> {
            throw new IllegalStateException("no");
        }));

        assertArrayEquals(original, req.getBody().getContent());
        assertEquals(original.length, req.getHeader().getContentLength());
    }

    /**
     * A mutation that fails halfway must not leave its half behind: the cached document goes, so
     * the next consumer sees the bytes the message arrived with rather than the failed change.
     */
    @Test
    void modifyDiscardsAPartialMutationWhenTheMutationThrows() {
        byte[] original = SOAP.getBytes(UTF_8);
        Request req = requestWith(original);

        assertThrows(IllegalStateException.class, () -> XmlDomBody.modify(req, doc -> {
            doc.getDocumentElement().setAttribute("half", "applied");
            throw new IllegalStateException("no");
        }));

        assertArrayEquals(original, req.getBody().getContent());
        assertEquals("", XmlDomBody.documentOf(req).getDocumentElement().getAttribute("half"));
    }

    @Test
    void xpathEvaluatesAgainstTheDocument() throws XPathExpressionException {
        Request req = requestWith(SOAP.getBytes(UTF_8));

        assertEquals("  spaced  ", XmlDomBody.xpath(req, "//*[local-name()='id']", null, STRING));
    }

    @Test
    void xpathResolvesThePrefixesOfTheCaller() throws XPathExpressionException {
        Request req = requestWith(SOAP.getBytes(UTF_8));

        assertEquals("  spaced  ", XmlDomBody.xpath(req, "//p:id", prefix("p", "http://example.com/p"), STRING));
        // A different caller may bind the same prefix elsewhere against the same cached document.
        assertEquals("", XmlDomBody.xpath(req, "//p:id", prefix("p", "http://example.com/other"), STRING));
    }

    @Test
    void xpathWithoutATypeYieldsTheExpressionsOwnType() throws XPathExpressionException {
        Request req = requestWith(SOAP.getBytes(UTF_8));

        assertEquals(1.0, XmlDomBody.xpath(req, "count(//*[local-name()='id'])", null).value());
    }

    /**
     * The point of the class: a chain of XPath using interceptors parses once, and the bytes stay
     * the ones that arrived.
     */
    @Test
    void xpathParsesOnlyOnce() throws XPathExpressionException {
        byte[] original = SOAP.getBytes(UTF_8);
        Request req = requestWith(original);

        XmlDomBody.xpath(req, "//*[local-name()='id']", null, STRING);
        Document first = XmlDomBody.documentOf(req);
        XmlDomBody.xpath(req, "//*[local-name()='order']", null, STRING);

        assertSame(first, XmlDomBody.documentOf(req));
        assertArrayEquals(original, req.getBody().getContent());
    }

    /**
     * The document a gzipped message is queried through is the decoded one, while the compressed
     * bytes stay on the message.
     */
    @Test
    void xpathOnAGzippedBodyQueriesTheDecodedDocument() throws IOException, XPathExpressionException {
        byte[] compressed = gzip(SOAP.getBytes(UTF_8));
        Request req = requestWith(compressed);
        req.getHeader().setValue(CONTENT_ENCODING, "gzip");

        assertEquals("  spaced  ", XmlDomBody.xpath(req, "//*[local-name()='id']", null, STRING));

        assertArrayEquals(compressed, req.getBody().getContent(), "must be forwarded as received");
        assertEquals("gzip", req.getHeader().getFirstValue(CONTENT_ENCODING));
    }

    private static NamespaceContext prefix(String prefix, String uri) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String p) {
                return prefix.equals(p) ? uri : NULL_NS_URI;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return uri.equals(namespaceURI) ? prefix : null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return List.of(prefix).iterator();
            }
        };
    }

    @Test
    void writesExactlyItsContent() throws IOException {
        Request req = requestWith(SOAP.getBytes(UTF_8));
        XmlDomBody.documentOf(req);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        req.getBody().write(new PlainBodyTransferer(out), true);

        assertArrayEquals(req.getBody().getContent(), out.toByteArray());
    }

    private static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(content);
        }
        return out.toByteArray();
    }
}
