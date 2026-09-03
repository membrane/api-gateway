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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.Rejected;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.Rewritten;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory.dtdAwareInputFactory;

/**
 * @description Prohibits XML documents to be passed through that look like XML attacks on older parsers. Too many
 * attributes, too long element names are such indications. DTD definitions will simply be removed.
 * @topic 3. Security and Validation
 */
@MCElement(name = "xmlProtection")
public class XMLProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XMLProtectionInterceptor.class.getName());
    public static final String X_PROTECTION = "X-Protection";
    private static final String POLICY_VIOLATED = "Content violates XML security policy";

    private int maxAttributeCount = 1000;
    private int maxElementNameLength = 1000;
    private int maxAttributeNameLength = 1000;
    private int maxDepth = -1;
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
        limits = new XMLLimits(maxElementNameLength, maxAttributeNameLength, maxAttributeCount, maxDepth, removeDTD);
        inputFactory = ThreadLocal.withInitial(() -> dtdAwareInputFactory(limits.jaxpNameLimit()));
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return handleInternal(exc);
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

    private Outcome handleInternal(Exchange exc) throws Exception {

        log.debug("Inspecting XML of content type: {}", exc.getRequest().getHeader().getContentType());

        if (exc.getRequest().isBodyEmpty()) {
            log.info("body is empty -> request is not scanned");
            return CONTINUE;
        }

        if (!exc.getRequest().isXML())
            return rejectNonXML(exc);

        if (protect(exc) instanceof Rejected rejected)
            return reject(exc, rejected);

        log.debug("protected against XML attacks");
        return CONTINUE;
    }

    /**
     * Scans the body, and replaces it only when the protector actually took something out of it, so
     * that a document nothing was removed from reaches the backend exactly as the client sent it
     * rather than as a re-serialised copy.
     */
    private XMLProtectionResult protect(Exchange exc) throws Exception {
        Request request = exc.getRequest();
        Charset charset;
        try {
            charset = resolveCharset(request);
        } catch (XMLStreamException e) {
            return invalidEncoding(e);
        }
        ByteArrayOutputStream protectedBody = new ByteArrayOutputStream();

        // msg.getBodyAsStreamDecoded() delivers an InputStream from bytes (Chunks) -> close should not be an issue
        try (OutputStreamWriter out = new OutputStreamWriter(protectedBody, charset);
             InputStreamReader in = new InputStreamReader(request.getBodyAsStreamDecoded(), charset)) {

            XMLProtectionResult result = new XMLProtector(out, inputFactory.get(), limits).protect(in);
            if (result instanceof Rewritten) {
                out.flush(); // ensure all bytes are written before reading
                request.setBodyContent(protectedBody.toByteArray()); // the DTD was removed
            }
            return result;
        }
    }

    /**
     * The HTTP charset the client declared takes precedence, matching the request as it was
     * labeled. Absent that, the document's own XML declaration decides - probed with a throwaway
     * {@link javax.xml.stream.XMLStreamReader}, so a document without an explicit {@code encoding}
     * still resolves to the XML default of UTF-8 rather than a guess that happens to also be UTF-8.
     */
    private Charset resolveCharset(Request request) throws Exception {
        String headerCharset = request.getHeader().getCharset();
        if (headerCharset != null)
            return Charset.forName(headerCharset);

        try (var body = request.getBodyAsStreamDecoded()) {
            return Charset.forName(inputFactory.get().createXMLStreamReader(body).getEncoding());
        }
    }

    /**
     * Naming the actual problem, rather than the generic "not well-formed" wording
     * {@link XMLProtector} uses once past the declaration: a broken or unsupported {@code encoding}
     * attribute is not a security concern, so telling the sender what to fix is safe here.
     */
    private static XMLProtectionResult invalidEncoding(XMLStreamException e) {
        Location loc = e.getLocation();
        return new Rejected("XML declaration has an invalid or unsupported encoding at line %d, column %d: %s"
                .formatted(loc != null ? loc.getLineNumber() : -1, loc != null ? loc.getColumnNumber() : -1, e.getMessage()));
    }

    private Outcome reject(Exchange exc, Rejected rejected) {
        log.info("Request was rejected by XML protection: {}", rejected.reason());
        security(router.getConfiguration().isProduction(), getDisplayName())
                .title(POLICY_VIOLATED)
                .status(400)
                .detail(rejected.reason())
                .buildAndSetResponse(exc);
        exc.getResponse().getHeader().add(X_PROTECTION, POLICY_VIOLATED);
        return ABORT;
    }

    private Outcome rejectNonXML(Exchange exc) {
        String msg = "Content-Type %s is not XML.".formatted(exc.getRequest().getHeader().getContentType());
        log.info(msg);
        user(router.getConfiguration().isProduction(), getDisplayName())
                .title("Request discarded by xmlProtection")
                .status(415)
                .detail(msg)
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
