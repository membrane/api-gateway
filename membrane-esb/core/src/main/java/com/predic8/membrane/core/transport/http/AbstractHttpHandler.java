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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.InterceptorFlowController;
import com.predic8.membrane.core.transport.Transport;

public abstract class AbstractHttpHandler  {

	protected Exchange exchange;
	protected Request srcReq;
	private static final InterceptorFlowController flowController = new InterceptorFlowController();
		
	private final Transport transport;
	
	public AbstractHttpHandler(Transport transport) {
		this.transport = transport;
	}

	public Transport getTransport() {
		return transport;
	}

	public abstract void shutdownInput() throws IOException;
	public abstract InetAddress getRemoteAddress() throws IOException;
	public abstract int getLocalPort();

	
	protected void invokeHandlers() throws Exception {
		try {
			flowController.invokeHandlers(exchange, transport.getInterceptors());
		} catch (Exception e) {
			if (exchange.getResponse() == null) {
				String msg;
				if (transport.isPrintStackTrace()) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					msg = sw.toString();
				} else {
					msg = e.toString();
				}
				exchange.setResponse(Response.interalServerError(msg).build());
			}
		} finally {
			if (exchange.getResponse() == null)
				exchange.setResponse(Response.interalServerError().build());
		}
	}

}
