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
import com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

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
        limits = new XMLLimits(maxElementNameLength, maxAttributeCount, maxDepth, removeDTD);
        inputFactory = ThreadLocal.withInitial(HardenedStaxInputFactory::dtdAwareInputFactory);
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
     * Scans the body and, unless it is rejected, replaces it with what the protector wrote - the
     * document minus its DTD.
     */
    private XMLProtectionResult protect(Exchange exc) throws Exception {
        Request request = exc.getRequest();
        var charset = request.getCharsetOrDefault();
        ByteArrayOutputStream protectedBody = new ByteArrayOutputStream();

        // msg.getBodyAsStreamDecoded() delivers an InputStream from bytes (Chunks) -> close should not be an issue
        try (OutputStreamWriter out = new OutputStreamWriter(protectedBody, charset);
             InputStreamReader in = new InputStreamReader(request.getBodyAsStreamDecoded(), charset)) {

            XMLProtectionResult result = new XMLProtector(out, inputFactory.get(), limits).protect(in);
            if (result instanceof Rejected)
                return result;

            out.flush(); // ensure all bytes are written before reading
            request.setBodyContent(protectedBody.toByteArray()); // Allow the removal of DTDs
            return result;
        }
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
     * @description If an incoming request exceeds this limit, it will be discarded.
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
