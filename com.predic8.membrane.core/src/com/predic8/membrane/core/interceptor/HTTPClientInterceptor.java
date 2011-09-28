/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import java.net.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.Proxies;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.HttpUtil;

public class HTTPClientInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(HTTPClientInterceptor.class.getName());

	public HTTPClientInterceptor() {
		name="HTTPClient";
		setFlow(Flow.REQUEST);
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		exc.blockRequestIfNeeded();
		
		Response targetRes = null;

		try {
			targetRes = getClient().call(exc);
			return Outcome.CONTINUE;
		} catch (ConnectException e) {
			targetRes = new ErrorResponse(500, "Internal Server Error", "Target " + getDestination(exc) + " is not reachable.");  
			log.warn("Target " + getDestination(exc) + " is not reachable. " + e);
			return Outcome.ABORT;
		} catch (UnknownHostException e) {
			targetRes = new ErrorResponse(500, "Internal Server Error", "Target host " + HttpUtil.getHostName(getDestination(exc)) + " is unknown. DNS was unable to resolve host name.");
			return Outcome.ABORT;
		} finally {
			exc.setResponse(targetRes);
			exc.getRule().addStatusCode(exc.getResponse().getStatusCode());
		}						
	}

	private String getDestination(Exchange exc) {
		return exc.getDestinations().get(0);
	}
	
	private HttpClient getClient() {
		HttpClient client = new HttpClient();
		Proxies cfg = router.getConfigurationManager().getProxies();
		client.setAdjustHostHeader(cfg.getAdjustHostHeader());
		client.setProxy(cfg.getProxyConfiguration());
		client.setMaxRetries(((HttpTransport)router.getTransport()).getHttpClientRetries());
		return client;
	}	
}
