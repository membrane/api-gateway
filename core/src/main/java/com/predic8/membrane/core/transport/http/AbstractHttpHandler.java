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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;

public abstract class AbstractHttpHandler  {

	private static final Logger log = LoggerFactory.getLogger(AbstractHttpHandler.class.getName());

	protected Exchange exchange;
	protected Request srcReq;
	private final Transport transport;

	public AbstractHttpHandler(Transport transport) {
		this.transport = transport;
	}

	public Transport getTransport() {
		return transport;
	}

	/**
	 * Only use for HTTP/1.0 requests. (see {@link HttpClient})
	 */
	public abstract void shutdownInput() throws IOException;
	public abstract InetAddress getLocalAddress();
	public abstract int getLocalPort();

	protected void invokeHandlers() throws AbortException, NoMoreRequestsException, EOFWhileReadingFirstLineException {
		try {
			getTransport().getRouter().getFlowController().invokeRequestHandlers(exchange, transport.getInterceptors());
			if (exchange.getResponse() == null) {
				log.info("Interceptor chain returned no response");
				internal(transport.getRouter().isProduction(),"http-handler")
						.detail("No response was generated by the interceptor chain.")
						.extension("interceptors", transport.getInterceptors())
						.buildAndSetResponse(exchange);
			}
		} catch (Exception e) {
			if (exchange.getResponse() == null)
				exchange.setResponse(generateErrorResponse(e));

			// TODO What is going on here?
            switch (e) {
                case AbortException abortException -> throw abortException;
                case NoMoreRequestsException noMoreRequestsException -> throw noMoreRequestsException;
                case NoResponseException noResponseException -> throw noResponseException;
                case EOFWhileReadingFirstLineException eofWhileReadingFirstLineException ->
                        throw eofWhileReadingFirstLineException;
                default -> {
                }
            }
            log.warn("An exception occurred while handling a request: ", e);
		}
	}

	private Response generateErrorResponse(Exception e) {
		return internal(transport.getRouter().isProduction(),"http-handler")
				.exception(e)
				.build();
	}

	/**
	 * @return whether the {@link #getLocalPort()} of the handler has to match
	 *         the rule's local port for the rule to apply.
	 */
	public boolean isMatchLocalPort() {
		return true;
	}

	/**
	 * @return the context path of our web application, when running as a servlet with removeContextRoot="true"
	 * (which is the default, when running as a servlet). returns an empty string otherwise (e.g. if not running
	 * as a servlet or when removeContextRoot="false")
	 */
	public String getContextPath(Exchange exc) {
		return "";
	}
}