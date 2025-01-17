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
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import javax.xml.transform.stream.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description <p>
 *              The transform feature applies an XSLT transformation to the content in the body of a message. After the
 *              transformation the body content is replaced with the result of the transformation.
 *              </p>
 * @topic 3. Enterprise Integration Patterns
 */
@MCElement(name="transform")
public class XSLTInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(XSLTInterceptor.class.getName());

	private String xslt;
	private volatile XSLTTransformer xsltTransformer;
	private final XOPReconstitutor xopr = new XOPReconstitutor();

	public XSLTInterceptor() {
		name = "XSLT Transformer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
        try {
            transformMsg(exc.getRequest(), xslt, exc.getStringProperties());
        } catch (Exception e) {
			ProblemDetails.user(router.isProduction())
					.component(getDisplayName())
					.detail("Error transforming request!")
					.exception(e)
					.stacktrace(true)
					.buildAndSetResponse(exc);
			return ABORT;
        }
        return CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
        try {
            transformMsg(exc.getResponse(), xslt, exc.getStringProperties());
        } catch (Exception e) {
			ProblemDetails.user(router.isProduction())
					.component(getDisplayName())
					.detail("Error transforming response!")
					.exception(e)
					.stacktrace(true)
					.buildAndSetResponse(exc);
			return ABORT;
        }
        return CONTINUE;
	}

	private void transformMsg(Message msg, String ss, Map<String, String> parameter) throws Exception {
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
        return TextUtil.removeFinalChar(getShortDescription()) +
               " using the stylesheet at " +
               TextUtil.linkURL(xslt) +
               " .";
	}

}
