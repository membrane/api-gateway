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
import com.predic8.membrane.core.util.xml.XPathUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A message body that additionally carries the already parsed XML {@link Document}, so that a chain
 * of XML aware interceptors parses the message once instead of once per interceptor. Since
 * {@link Message#setBody(AbstractBody)} and {@link Message#setBodyContent(byte[])} replace the body
 * reference wholesale, any byte level write by a non XML aware interceptor discards the document
 * along with it: a stale document is impossible by construction.
 * <p>
 * The bytes of an instance never change, so a change to the document has to be published to take
 * effect. Which entry point to use follows from what the caller does with the document:
 * <ul>
 *   <li>reading it — {@link #xpath(Message, String, NamespaceContext, QName)}, or
 *       {@link #read(Message, Function)} for a caller that needs the tree itself;</li>
 *   <li>changing it in place — {@link #modify(Message, Consumer)}, which publishes on return;</li>
 *   <li>producing a different document, as an XSLT transformation does —
 *       {@link #replaceBody(Message, Document)}.</li>
 * </ul>
 * Nor are the bytes necessarily the document's serialization: {@link #documentOf(Message)} keeps
 * the body a message arrived with, which for a gzipped or XOP body is not what was parsed.
 */
public class XmlDomBody extends AbstractBody {

    private static final Logger log = LoggerFactory.getLogger(XmlDomBody.class);

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
    static Document documentOf(Message msg) {
        if (msg.getBody() instanceof XmlDomBody xmlDomBody)
            return xmlDomBody.getDocument();
        byte[] content = msg.getBody().getContent();
        Document doc = msg.isEncoded() ? parseDecoded(msg) : parse(content);
        msg.setBody(new XmlDomBody(doc, content)); // Bytes unchanged, so the header still fits
        return doc;
    }

    /**
     * Evaluates an XPath expression against the message's document, parsing it only if no earlier
     * consumer already did. So a chain of XPath using interceptors costs one parse rather than one
     * per expression, which is what this body exists for.
     * <p>
     * The namespace bindings are the caller's, not the body's: two interceptors may query the same
     * cached document through different prefix mappings.
     *
     * @param namespaces the prefix bindings of the expression, or null if it uses none
     * @param type       the {@code XPathConstants} type to coerce the result to
     */
    public static Object xpath(Message msg, String expression, NamespaceContext namespaces, QName type)
            throws XPathExpressionException {
        return XPathUtil.newXPath(namespaces).evaluate(expression, documentOf(msg), type);
    }

    /**
     * Evaluates an XPath expression against the message's document, leaving the result in whatever
     * type the expression itself yields.
     *
     * @see #xpath(Message, String, NamespaceContext, QName)
     */
    public static XPathEvaluationResult<?> xpath(Message msg, String expression, NamespaceContext namespaces)
            throws XPathExpressionException {
        return XPathUtil.newXPath(namespaces).evaluateExpression(expression, documentOf(msg));
    }

    /**
     * Hands the message's document to a reader and returns what it made of it, without publishing
     * anything. For a caller whose work is not an XPath expression: WS-Security walks and rewrites
     * the tree itself.
     * <p>
     * The document is borrowed, not owned. A reader that changes it has to publish the change with
     * {@link #replaceBody(Message, Document)}, or use {@link #modify(Message, Consumer)} instead.
     * <p>
     * Parsing happens here, so an exception the reader does not throw itself is a malformed body.
     */
    public static <T> T read(Message msg, Function<Document, T> reader) {
        return reader.apply(documentOf(msg));
    }

    /**
     * Hands the message's document to a mutation and publishes the result, so that an interceptor
     * changing the XML in place cannot forget to. The document is the shared one: any consumer that
     * parsed before sees the change, and any that parses after gets the changed tree rather than a
     * re-parse.
     * <p>
     * A mutation that throws leaves the message's body as it was: nothing is published, and the
     * half-mutated document is dropped from the cache, so the next consumer parses the bytes the
     * message arrived with rather than inheriting the failed change. Only the cache is rolled back —
     * a caller still holding the {@link Document} of an earlier {@link #documentOf(Message)} keeps
     * the partially mutated tree it shares, now detached from the message.
     */
    public static void modify(Message msg, Consumer<Document> mutation) {
        Document doc = documentOf(msg);
        try {
            mutation.accept(doc);
        } catch (RuntimeException e) {
            // The bytes were never published, so they still fit the header: only the cached
            // document has to go.
            log.debug("Mutation failed, dropping the cached document so the body is re-parsed.", e);
            msg.setBody(new Body(msg.getBody().getContent()));
            throw e;
        }
        replaceBody(msg, doc);
    }

    /**
     * Replaces the message's body with the (possibly modified) document and adjusts the header
     * fields, as {@link Message#setBodyContent(byte[])} does.
     */
    public static void replaceBody(Message msg, Document doc) {
        msg.setBodyContent(new XmlDomBody(doc, serialize(doc)));
        correctContentTypeCharset(msg, encodingOf(doc));
    }

    /**
     * The encoding {@link #serialize(Document)} writes the document in.
     * <p>
     * Not simply UTF-8: the identity {@code Transformer} passes the document's own
     * {@link Document#getXmlEncoding()} to the serializer, which overrides the
     * {@link OutputKeys#ENCODING} property that {@code serialize} sets. So a document parsed from
     * an {@code encoding="ISO-8859-1"} declaration is written back as ISO-8859-1, and only a
     * document that never declared one comes out UTF-8.
     */
    private static String encodingOf(Document doc) {
        return doc.getXmlEncoding() != null ? doc.getXmlEncoding() : UTF_8.name();
    }

    /**
     * Makes the {@code Content-Type} charset agree with the bytes just written. RFC 7303 gives that
     * parameter precedence over the XML declaration, so a charset that says something else makes a
     * receiver misdecode every non-ASCII character - and makes a receiving WS-Security stack digest
     * bytes other than the ones that were signed.
     * <p>
     * Only a charset that is already there is corrected. One the sender omitted stays omitted,
     * because then the declaration decides and the two cannot contradict each other; adding a
     * parameter would rewrite the header of messages this does not affect.
     */
    private static void correctContentTypeCharset(Message msg, String writtenEncoding) {
        String declared = msg.getHeader().getCharset();
        if (declared == null || declared.equalsIgnoreCase(writtenEncoding)) {
            return;
        }
        try {
            ContentType contentType = msg.getHeader().getContentTypeObject();
            contentType.setParameter("charset", writtenEncoding);
            msg.getHeader().setContentType(contentType.toString());
        } catch (ParseException e) {
            // An unparseable Content-Type is not this method's problem to report: leave the header
            // as it stands rather than failing a body replacement over it.
            log.warn("Not correcting the charset of unparseable Content-Type \"{}\".",
                    msg.getHeader().getContentType());
        }
    }

    private static Document parse(byte[] content) {
        return HardenedXmlParser.getInstance().parse(new InputSource(new ByteArrayInputStream(content)));
    }

    /**
     * Serialization deliberately does not re-indent: reformatting would insert whitespace into
     * already-signed elements, invalidating their digests. The declaration the Transformer emits
     * carries a <tt>standalone</tt> pseudo-attribute, which is harmless — like the rest of the
     * declaration it sits outside the document element and is part of no digest.
     * <p>
     * {@link OutputKeys#ENCODING} is only the fallback, not a guarantee: the identity transform
     * hands the serializer the document's own {@link Document#getXmlEncoding()}, which wins whenever
     * the document was parsed from a declaration that named one. {@link #encodingOf(Document)} is
     * what actually gets written.
     */
    private static byte[] serialize(Document doc) {
        try {
            Transformer transformer = XMLUtil.newHardenedBestEffortTransformerFactory().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, UTF_8.name());
            // Writing to a stream rather than a Writer lets the Transformer apply the encoding
            // itself, so the declared encoding and the actual bytes cannot disagree.
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
     * Returns the live document. Mutating it does not change this body; mutate through
     * {@link #modify(Message, Consumer)}, which publishes the change.
     */
    Document getDocument() {
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
