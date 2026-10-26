/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.xml.parser.*;
import org.slf4j.*;

import javax.xml.xpath.*;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_RESPONSE_ABORT_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description <p>
 *              The <i>soapStackTraceFilter</i> removes SOAP stack traces from message bodies.
 *              </p>
 * @explanation <p>
 *              Using this interceptor hides sensitive information, as the structure of your backend source code, from the caller.
 *              </p>
 *              <p>
 *              The <i>soapStackTraceFilter</i> works without further configuration with most backend servers, but it is
 *              advised to test its functionality in combination with your SOAP service provider before deploying it in
 *              production.
 *              </p>
 *              <p>
 *              A message whose body cannot be parsed as XML, e.g. because it contains a DOCTYPE, is rejected: with
 *              status 400 in the request flow and 500 in the response flow. An empty body is left unchanged.
 *              </p>
 * @topic 6. Web Services with SOAP and WSDL
 */
@MCElement(name="soapStackTraceFilter")
public class SOAPStackTraceFilterInterceptor extends AbstractInterceptor {

	private static final Logger LOG = LoggerFactory.getLogger(SOAPStackTraceFilterInterceptor.class);
	private static final String XPATH =
			"""
			//*[local-name()='Fault' and namespace-uri()='%s']//*[local-name()='stackTrace' or local-name()='stacktrace'] | 
			//*[local-name()='Fault' and namespace-uri()='%s']//*[local-name()='faultstring' and contains(., '.java:')] | 
			//*[local-name()='Fault' and namespace-uri()='%s']//*[local-name()='exception' and namespace-uri()='http://jax-ws.dev.java.net/']/message |
			//*[local-name()='Fault' and namespace-uri()='%s']//detail/Exception
			""".formatted(SOAP11_NS,SOAP11_NS,SOAP11_NS,SOAP11_NS);

	private final XMLContentFilter xmlContentFilter;

	public SOAPStackTraceFilterInterceptor() throws XPathExpressionException {
		this.xmlContentFilter = new XMLContentFilter(XPATH);
		setDisplayName("soap stacktrace filter");
		setAppliedFlow(REQUEST_RESPONSE_ABORT_FLOW);
	}

	@Override
	public String getShortDescription() {
		return "Removes SOAP stacktraces from envelopes.";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		return handleMessage(exc, exc.getRequest());
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
		return handleMessage(exc, exc.getResponse());
	}

	private Outcome handleMessage(Exchange exc, Message message) {
		try {
			xmlContentFilter.removeMatchingElements(message);
			return CONTINUE;
		} catch (ReadingBodyException e) {
			// Reported centrally: a body that could not be read is no XML error, and the flow controller tells a
			// request body apart from a backend response body.
			throw e;
		} catch (XmlParseException e) {
			LOG.info("Rejected message: {}", e.getMessage());
			(message instanceof Request ? user(router.getConfiguration().isProduction(), getDisplayName())
					: internal(router.getConfiguration().isProduction(), getDisplayName()))
					.title("XML Error")
					.detail(e.getMessage())
					.buildAndSetResponse(exc);
			return ABORT;
		} catch (Exception e) {
			LOG.debug("soapStackTraceFilter error", e);
			LOG.info("Rejected message: {}", e.getMessage());
			internal(router.getConfiguration().isProduction(), getDisplayName())
					.title("XML Error")
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
		}
	}

}
