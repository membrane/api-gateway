/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.rest;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.soap.*;
import org.slf4j.*;

import javax.xml.transform.stream.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Converts SOAP messages into REST requests.
 * @topic 5. Web Services with SOAP and WSDL
 */
@MCElement(name="soap2Rest")
public class SOAP2RESTInterceptor extends SOAPRESTHelper {

	private static final Logger log = LoggerFactory.getLogger(SOAP2RESTInterceptor.class.getName());

	private String requestXSLT;
	private String responseXSLT;

	private SoapOperationExtractor soe = new SoapOperationExtractor();
	private DispatchingInterceptor di = new DispatchingInterceptor();

	public SOAP2RESTInterceptor() {
		name = "soap 2 rest gateway";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		// save SOAP operationName and namespace in exchange properties to generically construct response name
		soe.handleRequest(exc);

		// apply request XSLT
        try {
            transformAndReplaceBody(exc.getRequest(), requestXSLT, new StreamSource(exc.getRequest().getBodyAsStreamDecoded()), exc.getStringProperties());
        } catch (Exception e) {
			log.error("", e);
			user(router.isProduction(),getDisplayName())
					.detail("Could not transform using XSLT!")
					.exception(e)
					.stacktrace(true)
					.buildAndSetResponse(exc);
			return ABORT;
        }

        // fill Request object from HTTP-XML
		Header header = exc.getRequest().getHeader();
		header.removeFields(CONTENT_TYPE);
		header.setContentType(TEXT_XML_UTF8);
		XML2HTTP.unwrapMessageIfNecessary(exc.getRequest());

		// reset exchange destination to new request URI
		exc.getDestinations().clear();
		di.handleRequest(exc);

		return CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
        try {
            transformAndReplaceBody(exc.getResponse(), responseXSLT, getExchangeXMLSource(exc), exc.getStringProperties());
        } catch (Exception e) {
			log.error("", e);
			user(router.isProduction(),getDisplayName())
					.detail("Could not transform using XSLT!")
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
        }
        return CONTINUE;
	}

	@Override
	public String getShortDescription() {
		return "Transforms SOAP messages into REST requests and vice versa.";
	}

	public String getRequestXSLT() {
		return requestXSLT;
	}

	@MCAttribute
	public void setRequestXSLT(String requestXSLT) {
		this.requestXSLT = requestXSLT;
	}

	public String getResponseXSLT() {
		return responseXSLT;
	}

	@MCAttribute
	public void setResponseXSLT(String responseXSLT) {
		this.responseXSLT = responseXSLT;
	}

}
