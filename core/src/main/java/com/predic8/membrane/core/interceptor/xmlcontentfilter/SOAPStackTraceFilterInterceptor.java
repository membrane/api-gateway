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
import org.slf4j.*;

import javax.xml.xpath.*;

import static com.predic8.membrane.core.Constants.*;
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
 * @topic 8. SOAP based Web Services
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
		setFlow(REQUEST_RESPONSE_ABORT_FLOW);
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
		} catch (Exception e) {
			LOG.error("soapStackTraceFilter error", e);
			exc.setResponse(Response.internalServerError("soapStackTraceFilter error. See log for details.").build());
			return ABORT;
		}
	}

}
