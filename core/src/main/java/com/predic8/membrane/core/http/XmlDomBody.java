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

import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A message body that additionally carries the already parsed XML {@link Document}, so that a chain
 * of XML aware interceptors parses the message once instead of once per interceptor. Since
 * {@link Message#setBody(AbstractBody)} and {@link Message#setBodyContent(byte[])} replace the body
 * reference wholesale, any byte level write by a non XML aware interceptor discards the document
 * along with it: a stale document is impossible by construction.
 * <p>
 * The bytes of an instance never change. Mutating the {@link Document} returned by
 * {@link #getDocument()} therefore does <b>not</b> update the message; call
 * {@link #replaceBody(Message, Document)} to publish the modified document. Nor are the bytes
 * necessarily the document's serialization: {@link #documentOf(Message)} keeps the body a message
 * arrived with, which for a gzipped or XOP body is not what was parsed.
 */
public class XmlDomBody extends AbstractBody {

    private final Document document;

    private XmlDomBody(Document document, byte[] content) {
        this.document = document;
        chunks.add(new Chunk(content));
        markAsRead(); // because we do not have something to read
    }

    /**
     * Returns the parsed document of the message, caching it on the message so that a chain of XML
     * aware interceptors parses only once.
     * <p>
     * The body keeps the bytes it arrived with, so the headers describing them stay correct and the
     * message is forwarded exactly as received. For an encoded body those bytes are therefore not
     * the document's serialization: the document is parsed from
     * {@link Message#getBodyAsStreamDecoded()}, which un-gzips and reassembles XOP multiparts.
     * Publishing a decoded body is {@link #replaceBody(Message, Document)}'s job, not this
     * method's.
     */
    public static Document documentOf(Message msg) {
        if (msg.getBody() instanceof XmlDomBody xmlDomBody)
            return xmlDomBody.getDocument();
        byte[] content = msg.getBody().getContent();
        Document doc = msg.isEncoded() ? parseDecoded(msg) : parse(content);
        msg.setBody(new XmlDomBody(doc, content)); // Bytes unchanged, so the header still fits
        return doc;
    }

    /**
     * Replaces the message's body with the (possibly modified) document and adjusts the header
     * fields, as {@link Message#setBodyContent(byte[])} does.
     */
    public static void replaceBody(Message msg, Document doc) {
        msg.setBodyContent(new XmlDomBody(doc, serialize(doc)));
    }

    private static Document parse(byte[] content) {
        return HardenedXmlParser.getInstance().parse(new InputSource(new ByteArrayInputStream(content)));
    }

    /**
     * Serialization deliberately does not re-indent: reformatting would insert whitespace into
     * already-signed elements, invalidating their digests. The declaration the Transformer emits
     * carries a <tt>standalone</tt> pseudo-attribute, which is harmless — like the rest of the
     * declaration it sits outside the document element and is part of no digest.
     */
    private static byte[] serialize(Document doc) {
        try {
            Transformer transformer = XMLUtil.newHardenedBestEffortTransformerFactory().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            // Writing to a stream rather than a Writer lets the Transformer apply ENCODING itself,
            // so the declared encoding and the actual bytes cannot disagree.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException e) {
            throw new WritingBodyException(e);
        }
    }

    /**
     * Parses the decoded body, streaming it into the parser rather than materializing it as a
     * second array. The stream is closed because for a gzip or brotli body it holds a native
     * decompressor, which is then released here instead of whenever the Cleaner gets to it.
     */
    private static Document parseDecoded(Message msg) {
        try (InputStream in = msg.getBodyAsStreamDecoded()) {
            return HardenedXmlParser.getInstance().parse(new InputSource(in));
        } catch (IOException e) {
            throw new ReadingBodyException(e);
        }
    }

    /**
     * Returns the live document. Mutating it does not change this body; publish the changes with
     * {@link #replaceBody(Message, Document)}.
     */
    public Document getDocument() {
        return document;
    }

    @Override
    protected void readLocal() {
        // nothing to read, the content was passed to the constructor
    }

    @Override
    protected void writeAlreadyRead(AbstractBodyTransferer out) throws IOException {
        byte[] content = getContent();
        if (content.length > 0)
            out.write(content, 0, content.length);
        out.finish(null);
    }

    @Override
    protected void writeNotRead(AbstractBodyTransferer out) throws IOException {
        writeAlreadyRead(out);
    }

    @Override
    protected void writeStreamed(AbstractBodyTransferer out) {
        try {
            writeAlreadyRead(out);
        } catch (IOException e) {
            throw new WritingBodyException(e);
        }
    }

    @Override
    protected byte[] getRawLocal() {
        return getContent();
    }
}
