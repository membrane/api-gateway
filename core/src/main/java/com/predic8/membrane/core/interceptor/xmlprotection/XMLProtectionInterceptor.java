/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.google.common.io.CountingInputStream;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.protection.AbstractBodyProtectionInterceptor;
import com.predic8.membrane.core.interceptor.protection.Origin;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.Rejected;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.Rewritten;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.charset.Charset;
import java.util.function.Supplier;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.MimeType.isXML;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLLimits.UNLIMITED;
import static com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory.dtdAwareInputFactory;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description <p>Prohibits XML documents to be passed through that look like XML attacks on older parsers. Too many
 * attributes, too long element names are such indications. DTD definitions will simply be removed.</p>
 * <p>XML documents carried inside a multipart body are inspected part by part, so a document uploaded
 * as an attachment is checked like a plain XML body. A part the plugin had to take a DTD out of is
 * written back into the body; the other parts pass through unchanged.</p>
 *
 * @yaml
 * <pre><code>
 * - xmlProtection:
 *     maxAttributeCount: 1000
 *     maxElementNameLength: 1000
 *     maxAttributeNameLength: 1000
 *     maxDepth: 50
 *     maxSize: 10000000
 *     removeDTD: true
 *     otherContentTypes: SKIP
 * </code></pre>
 *
 * @topic 3. Security and Validation
 */
@MCElement(name = "xmlProtection")
public class XMLProtectionInterceptor extends AbstractBodyProtectionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XMLProtectionInterceptor.class.getName());
    public static final String X_PROTECTION = "X-Protection";
    private static final String POLICY_VIOLATED = "Content violates XML security policy";

    private int maxAttributeCount = 1000;
    private int maxElementNameLength = 1000;
    private int maxAttributeNameLength = 1000;
    private int maxDepth = -1;
    private int maxSize = 100 * 1024 * 1024;
    private boolean removeDTD = true;

    private XMLLimits limits;

    /**
     * Hardened parser factories are expensive to build and are not shared between threads, so each
     * thread keeps the one it built. Created once per interceptor, not once per request.
     */
    private ThreadLocal<XMLInputFactory> inputFactory;

    public XMLProtectionInterceptor() {
        name = "xml protection";
        setAppliedFlow(REQUEST_FLOW);
    }

    @Override
    public void init() {
        super.init();
        limits = new XMLLimits(maxElementNameLength, maxAttributeNameLength, maxAttributeCount, maxDepth, maxSize, removeDTD);
        inputFactory = ThreadLocal.withInitial(() -> dtdAwareInputFactory(limits.jaxpNameLimit()));
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return protect(exc, exc.getRequest());
        } catch (Exception e) {
            log.info("Could not inspect the XML body: {}", e.getMessage());
            log.debug("", e);
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .status(500)
                    .detail("Error inspecting body!")
                    .internal("reason", e.getMessage())
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    @Override
    protected boolean inspects(String contentType) {
        return isXML(contentType);
    }

    /**
     * Applied per document, so in a multipart body no single XML part may exceed it. To cap the size
     * of the entire request, use the <code>limit</code> plugin.
     */
    @Override
    protected int maxDocumentSize() {
        return limits.maxSize() == UNLIMITED ? Integer.MAX_VALUE : limits.maxSize();
    }

    /**
     * A document without a Content-Type is not XML, whether it is a whole body or one part. This
     * differs from <code>jsonProtection</code>, which parses an undeclared body anyway: rejecting an
     * undeclared body is what this plugin has always done, and an XML document the sender did not
     * label is better sent back than guessed at.
     */
    @Override
    protected boolean holdsInspectableContent(Origin origin) {
        return origin.contentType() != null && inspects(origin.contentType());
    }

    /**
     * Resolves the encoding the document is written in before handing it to the protector: a broken
     * encoding declaration is reported for what it is rather than as malformed XML.
     */
    @Override
    protected Inspection inspectDocument(Exchange exc, Supplier<InputStream> document, Origin origin) {
        log.debug("Inspecting XML of content type: {}", origin.contentType());

        final Charset charset;
        try {
            charset = resolveCharset(origin.header(), document);
        } catch (XMLStreamException e) {
            // Naming the actual problem, rather than the generic "not well-formed" wording
            // XMLProtector uses once past the declaration: a broken or unsupported encoding
            // attribute is not a security concern, so telling the sender what to fix is safe here.
            return Inspection.failed(reject(exc, Rejected.at("XML declaration has an invalid or unsupported encoding", e), origin));
        }
        return scanAndRewrite(exc, document.get(), charset, origin);
    }

    /**
     * Scans the document, and hands back the protector's copy of it only when the protector actually
     * took something out - so a document nothing was removed from reaches the backend exactly as the
     * client sent it rather than as a re-serialised copy.
     */
    private Inspection scanAndRewrite(Exchange exc, InputStream document, Charset charset, Origin origin) {
        ByteArrayOutputStream protectedDocument = new ByteArrayOutputStream();
        CountingInputStream counting = new CountingInputStream(document);

        // The streams read from bytes already held by the message -> closing them is not an issue.
        try (OutputStreamWriter out = new OutputStreamWriter(protectedDocument, charset);
             InputStreamReader in = new InputStreamReader(counting, charset)) {

            XMLProtectionResult result = new XMLProtector(out, inputFactory.get(), limits, charset, counting::getCount)
                    .protect(in);
            if (result instanceof Rejected rejected)
                return Inspection.failed(reject(exc, rejected, origin));
            if (result instanceof Rewritten) {
                out.flush(); // ensure all bytes are written before reading
                return Inspection.rewritten(protectedDocument.toByteArray()); // the DTD was removed
            }
        } catch (XMLStreamException | IOException e) {
            return Inspection.failed(rejectUnprocessableBody(exc, origin, e.getMessage()));
        }
        log.debug("protected against XML attacks");
        return Inspection.passed();
    }

    /**
     * The HTTP charset the sender declared takes precedence, matching the document as it was
     * labeled. Absent that, the document's own XML declaration decides - probed with a throwaway
     * {@link XMLStreamReader} over a second read of the document, so a document without an explicit
     * {@code encoding} still resolves to the XML default of UTF-8 rather than a guess that happens to
     * also be UTF-8.
     *
     * @param header the header the document was labeled by: the message's, or the part's
     */
    private Charset resolveCharset(Header header, Supplier<InputStream> document) throws XMLStreamException {
        final String headerCharset = header.getCharset();
        if (headerCharset != null)
            return Charset.forName(headerCharset);

        try (var probed = document.get()) {
            final XMLStreamReader probe = inputFactory.get().createXMLStreamReader(probed);
            try {
                // A reader need not report an encoding; the XML default applies when it does not.
                return probe.getEncoding() != null ? Charset.forName(probe.getEncoding()) : UTF_8;
            } finally {
                probe.close(); // XMLStreamReader is not AutoCloseable; leaves the stream to the block above
            }
        } catch (IOException e) {
            throw new XMLStreamException("Could not read the document to probe its encoding.", e);
        }
    }

    private Outcome reject(Exchange exc, Rejected rejected, Origin origin) {
        log.info("Request was rejected by XML protection: {}", rejected.reason());
        security(router.getConfiguration().isProduction(), getDisplayName())
                .title(POLICY_VIOLATED)
                .status(400)
                .detail(origin.describe(rejected.reason()))
                .buildAndSetResponse(exc);
        exc.getResponse().getHeader().add(X_PROTECTION, POLICY_VIOLATED);
        return ABORT;
    }

    @Override
    protected Outcome rejectOtherContentType(Exchange exc, Origin origin) {
        String msg = "Content-Type %s is not XML. Set otherContentTypes to \"skip\" to pass non-XML content through."
                .formatted(origin.contentType());
        log.info(msg);
        user(router.getConfiguration().isProduction(), getDisplayName())
                .title("Request discarded by xmlProtection")
                .status(415)
                .detail(origin.describe(msg))
                .buildAndSetResponse(exc);
        return ABORT;
    }

    /**
     * A multipart body that cannot be traversed or put back together is the sender's fault, not the
     * gateway's, so it is a 400 rather than the 500 an unexpected failure gets.
     */
    @Override
    protected Outcome rejectUnprocessableBody(Exchange exc, Origin origin, String reason) {
        log.info("Request was rejected by XML protection: {}", reason);
        user(router.getConfiguration().isProduction(), getDisplayName())
                .title("Request discarded by xmlProtection")
                .status(400)
                .detail(origin.describe(reason))
                .buildAndSetResponse(exc);
        return ABORT;
    }

    /**
     * @description If an incoming request exceeds this limit, it will be discarded.
     * @default 1000
     */
    @MCAttribute
    public void setMaxAttributeCount(int maxAttributeCount) {
        this.maxAttributeCount = maxAttributeCount;
    }

    public int getMaxAttributeCount() {
        return maxAttributeCount;
    }

    /**
     * @description Maximum length of an element name, counted as the document spells it, so a
     * qualified name like <code>ns:order</code> counts its prefix and colon too. If an incoming
     * request exceeds this limit, it will be discarded. A value of -1 disables the limit.
     * @default 1000
     */
    @MCAttribute
    public void setMaxElementNameLength(int maxElementNameLength) {
        this.maxElementNameLength = maxElementNameLength;
    }

    public int getMaxElementNameLength() {
        return maxElementNameLength;
    }

    /**
     * @description Maximum length of an attribute name, counted as the document spells it, so a
     * qualified name like <code>ns:id</code> counts its prefix and colon too. If an incoming request
     * exceeds this limit, it will be discarded. A value of -1 disables the limit.
     * @default 1000
     */
    @MCAttribute
    public void setMaxAttributeNameLength(int maxAttributeNameLength) {
        this.maxAttributeNameLength = maxAttributeNameLength;
    }

    public int getMaxAttributeNameLength() {
        return maxAttributeNameLength;
    }

    /**
     * @description Maximum nesting depth of XML elements. If an incoming request exceeds this limit, it will be
     * discarded. A value of -1 disables the limit.
     * @default -1 (unlimited)
     */
    @MCAttribute
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * @description Maximum size in bytes of a single XML document. The limit is per document, so in a
     * multipart body it applies to each XML part separately rather than to the whole upload. To cap
     * the size of the entire request, use the <code>limit</code> plugin with its
     * <code>maxBodyLength</code> attribute. A value of -1 disables the limit.
     * @default 104857600
     */
    @MCAttribute
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    /**
     * @description Whether to remove the DTD from incoming requests.
     * @default true
     */
    @MCAttribute
    public void setRemoveDTD(boolean removeDTD) {
        this.removeDTD = removeDTD;
    }

    public boolean isRemoveDTD() {
        return removeDTD;
    }

    @Override
    public String getShortDescription() {
        return "Protects against XML attacks.";
    }

}
