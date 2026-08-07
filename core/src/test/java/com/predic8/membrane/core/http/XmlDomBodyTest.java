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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class XmlDomBodyTest {

    private static final String SOAP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body><p:order xmlns:p="http://example.com/p"><p:id>  spaced  </p:id></p:order></s:Body>
            </s:Envelope>""";

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
