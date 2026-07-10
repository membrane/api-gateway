/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xslt;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.ExceptionUtil.getRootCause;
import static com.predic8.membrane.core.util.text.StringUtil.tail;
import static com.predic8.membrane.core.util.text.StringUtil.truncateAfter;
import static com.predic8.membrane.core.util.text.TextUtil.linkURL;
import static com.predic8.membrane.core.util.text.TextUtil.removeFinalChar;

/**
 * @description Applies an XSLT stylesheet to the body of a request or response and replaces the body with
 * the transformation result. Every string-valued <code>Exchange</code> property is passed to the stylesheet
 * as an XSLT parameter of the same name; declare a matching <code>xsl:param</code> in the stylesheet to read
 * it. Set such a property beforehand, e.g. with <code>setProperty</code>. See
 * tutorials/xml/35-XSLT-Transformation-to-json.yaml.
 * @topic 2. Enterprise Integration Patterns
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - setProperty:
 *         name: company
 *         value: predic8
 *     - transform:
 *         xslt: customer2person.xsl
 * </code></pre>
 * @explanation <code>customer2person.xsl</code> declares a matching <code>xsl:param</code> and reads it as
 * <code>$company</code>:
 * <pre><code>
 * &lt;xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"&gt;
 *   &lt;xsl:param name="company"/&gt;
 *   &lt;xsl:template match="/customer"&gt;
 *     &lt;person&gt;
 *       &lt;company&gt;&lt;xsl:value-of select="$company"/&gt;&lt;/company&gt;
 *     &lt;/person&gt;
 *   &lt;/xsl:template&gt;
 * &lt;/xsl:stylesheet&gt;
 * </code></pre>
 */
@MCElement(name = "transform")
public class XSLTInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XSLTInterceptor.class.getName());

    private String xslt;
    private volatile XSLTTransformer xsltTransformer;
    private final XOPReconstitutor xopr = new XOPReconstitutor();

    public XSLTInterceptor() {
        name = "xslt transformer";
    }

    @Override
    public void init() {
        super.init();
        try {
            xsltTransformer = new XSLTTransformer(xslt, router, getBeanBaseLocation(), getConcurrency());
        } catch (Exception e) {
            log.debug("", e);
            throw new ConfigurationException("Could not create XSLT transformer from: %s".formatted(xslt), e);

        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        var msg = exc.getMessage(flow);

        try {
            transformMsg(msg, exc.getStringProperties());
        } catch (TransformerException e) {
            log.debug("", e);
            var cause = getRootCause(e);
            // rolog matches Prolog and prolog
            if (cause.getMessage() != null && cause.getMessage().contains("rolog")) {
                user(router.getConfiguration().isProduction(), getDisplayName())
                        .title("Content not allowed in prolog of XML input.")
                        .detail("Check for extra characters before the XML declaration <?xml ... ?>")
                        .internal("offendingInput", truncateAfter(msg.getBodyAsStringDecoded() + "...", 50))
                        .stacktrace(false)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            if (cause.getMessage() != null && cause.getMessage().contains("is not allowed in trailing section")) {
                user(router.getConfiguration().isProduction(), getDisplayName())
                        .title("Content not allowed in trailing section of XML input.")
                        .detail("Check for extra characters after the XML root element (after the final closing tag like </root>).")
                        .internal("offendingInput", tail(msg.getBodyAsStringDecoded(), 50))
                        .stacktrace(false)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            if (cause.getMessage() != null && cause.getMessage().contains("No such file")) {
                  internal(router.getConfiguration().isProduction(), getDisplayName())
                        .title("XSLT transformation failed")
                        .detail(cause.getMessage())
                        .stacktrace(false)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            return createErrorResponse(exc, cause, flow);
        } catch (Exception e) {
            log.info("", e);
            return createErrorResponse(exc, e, flow);
        }
        return CONTINUE;
    }

    private @NotNull Outcome createErrorResponse(Exchange exc, Throwable e, Flow flow) {
        user(router.getConfiguration().isProduction(), getDisplayName())
                .detail("Error transforming message!")
                .exception(e)
                .internal("flow", flow.toString())
                .buildAndSetResponse(exc);
        return ABORT;
    }

    private void transformMsg(Message msg, Map<String, String> parameter) throws Exception {
        if (msg.isBodyEmpty())
            return;
        msg.setBodyContent(xsltTransformer.transform(
                new StreamSource(xopr.reconstituteIfNecessary(msg)), parameter));
    }

    private static int getConcurrency() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }

    public String getXslt() {
        return xslt;
    }

    /**
     * @description Location of the XSLT stylesheet that will be applied to request and response.
     * @example strip.xslt
     */
    @MCAttribute
    public void setXslt(String xslt) {
        this.xslt = xslt;
        this.xsltTransformer = null;
    }

    @Override
    public String getShortDescription() {
        return "Applies an XSLT transformation.";
    }

    @Override
    public String getLongDescription() {
        return "%s using the stylesheet at %s .".formatted(removeFinalChar(getShortDescription()), linkURL(xslt));
    }

}
