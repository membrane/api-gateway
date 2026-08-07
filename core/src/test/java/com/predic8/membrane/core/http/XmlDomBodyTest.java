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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.Header.CONTENT_ENCODING;
import static com.predic8.membrane.core.http.Header.TRANSFER_ENCODING;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
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
