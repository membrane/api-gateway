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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.util.text.StringUtil.*;
import static com.predic8.membrane.core.util.text.TextUtil.*;

/**
 * @description <p>
 * The transform feature applies an XSLT transformation to the content in the body of a message. After the
 * transformation the body content is replaced with the result of the transformation.
 * </p>
 * @topic 2. Enterprise Integration Patterns
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
            if (e.getMessage() != null && e.getMessage().contains("not allowed in prolog")) {
                user(router.getConfiguration().isProduction(), getDisplayName())
                        .title("Content not allowed in prolog of XML input.")
                        .detail("Check for extra characters before the XML declaration <?xml ... ?>")
                        .internal("offendingInput", truncateAfter(msg.getBodyAsStringDecoded() + "...", 50))
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            if (e.getMessage() != null && e.getMessage().contains("is not allowed in trailing section")) {
                user(router.getConfiguration().isProduction(), getDisplayName())
                        .title("Content not allowed in trailing section of XML input.")
                        .detail("Check for extra characters after the XML root element (after the final closing tag like </root>).")
                        .internal("offendingInput", tail(msg.getBodyAsStringDecoded(), 50))
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            return createErrorResponse(exc,e,flow);
        } catch (Exception e) {
            log.info("", e);
            return createErrorResponse(exc,e,flow);
        }
        return CONTINUE;
    }

    private @NotNull Outcome createErrorResponse(Exchange exc, Exception e, Flow flow) {
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

    @Override
    public void init() {
        super.init();
        try {
            xsltTransformer = new XSLTTransformer(xslt, router, getConcurrency());
        } catch (Exception e) {
			      log.debug("",e);
            throw new ConfigurationException("Could not create XSLT transformer",e);

        }
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
