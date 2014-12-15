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

import javax.xml.xpath.XPathExpressionException;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

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
	private static final String XPATH = ""
			+ "//*[local-name()='Fault' and namespace-uri()='" + Constants.SOAP11_NS + "']//*[local-name()='stackTrace' or local-name()='stacktrace'] | "
			+ "//*[local-name()='Fault' and namespace-uri()='" + Constants.SOAP11_NS + "']//*[local-name()='faultstring' and contains(., '.java:')] | "
			+ "//*[local-name()='Fault' and namespace-uri()='" + Constants.SOAP11_NS + "']//*[local-name()='exception' and namespace-uri()='http://jax-ws.dev.java.net/']/message |"
			+ "//*[local-name()='Fault' and namespace-uri()='" + Constants.SOAP11_NS + "']//detail/Exception";

	private final XMLContentFilter xmlContentFilter;

	public SOAPStackTraceFilterInterceptor() throws XPathExpressionException {
		this.xmlContentFilter = new XMLContentFilter(XPATH);
		setDisplayName("SOAP StackTrace Filter");
		setFlow(Flow.Set.REQUEST_RESPONSE);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return handleMessage(exc, exc.getRequest());
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		return handleMessage(exc, exc.getResponse());
	}

	private Outcome handleMessage(Exchange exc, Message message) {
		try {
			xmlContentFilter.removeMatchingElements(message);
			return Outcome.CONTINUE;
		} catch (Exception e) {
			LOG.error("soapStackTraceFilter error", e);
			exc.setResponse(Response.internalServerError("soapStackTraceFilter error. See log for details.").build());
			return Outcome.ABORT;
		}
	}

}
