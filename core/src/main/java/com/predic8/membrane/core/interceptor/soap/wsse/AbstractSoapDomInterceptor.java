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
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.SOAPUtil;
import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;

/**
 * Base class for the WS-Security interceptors that operate on the request's SOAP envelope as a
 * DOM tree: it rejects non-SOAP requests, parses the body, and turns any unexpected failure into
 * a Problem Details response, leaving subclasses with just their WS-Security logic.
 */
abstract class AbstractSoapDomInterceptor extends AbstractInterceptor {

    // Instance logger so log lines are still attributed to the concrete interceptor.
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Completes "Request body is not XML or does not contain a SOAP body, so ...", e.g.
     * "no wsu:Timestamp could be added.".
     */
    protected abstract String notSoapDetail();

    /**
     * The detail of the 500 response used when handling fails unexpectedly, e.g.
     * "Could not add wsu:Timestamp to SOAP body.".
     */
    protected abstract String internalErrorDetail();

    /**
     * Handles the parsed SOAP envelope. Implementations that modify it are responsible for
     * calling {@link #writeBack(Exchange, Document)}.
     */
    protected abstract Outcome handleDocument(Exchange exc, Document doc) throws Exception;

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), exc.getRequest()).isSOAP()) {
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Not a SOAP message.")
                    .detail("Request body is not XML or does not contain a SOAP body, so " + notSoapDetail())
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        try {
            return handleDocument(exc, HardenedXmlParser.getInstance().parse(XMLUtil.getInputSource(exc.getRequest())));
        } catch (Exception e) {
            log.warn(internalErrorDetail(), e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail(internalErrorDetail())
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    /**
     * Replaces the request body with the (possibly modified) document. Serialization deliberately
     * does not re-indent: reformatting would insert whitespace into already-signed elements,
     * invalidating their digests.
     */
    protected static void writeBack(Exchange exc, Document doc) throws TransformerException {
        Transformer transformer = XMLUtil.newHardenedBestEffortTransformerFactory().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        exc.getRequest().setBodyContent(writer.toString().getBytes(StandardCharsets.UTF_8));
    }
}
