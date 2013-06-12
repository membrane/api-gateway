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
package com.predic8.membrane.core.interceptor;

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.HttpUtil;

@MCElement(name="httpClient")
public class HTTPClientInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(HTTPClientInterceptor.class.getName());

	private boolean failOverOn5XX;
	private boolean adjustHostHeader = true;
	private HttpClientConfiguration httpClientConfig;
	
	private HttpClient hc;
	
	public HTTPClientInterceptor() {
		name="HTTPClient";
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		exc.blockRequestIfNeeded();
		
		Response targetRes = null;

		try {
			targetRes = hc.call(exc, adjustHostHeader, failOverOn5XX);
			return Outcome.RETURN;
		} catch (ConnectException e) {
			targetRes = Response.badGateway("Target " + getDestination(exc) + " is not reachable.").build();
			log.warn("Target " + getDestination(exc) + " is not reachable. " + e);
			return Outcome.ABORT;
		} catch (UnknownHostException e) {
			targetRes = Response.interalServerError("Target host " + HttpUtil.getHostName(getDestination(exc)) + " is unknown. DNS was unable to resolve host name.").build();
			return Outcome.ABORT;
		} finally {
			exc.setResponse(targetRes);
		}						
	}

	private String getDestination(Exchange exc) {
		return exc.getDestinations().get(0);
	}
	
	@Override
	public void init(Router router) throws Exception {
		super.init(router);

		if (httpClientConfig == null)
			hc = router.getResolverMap().getHTTPSchemaResolver().getHttpClient();
		else
			hc = new HttpClient(httpClientConfig);
	}

	
	@Override
	public String getHelpId() {
		return "http-client";
	}
	
	public boolean isFailOverOn5XX() {
		return failOverOn5XX;
	}
	
	@MCAttribute
	public void setFailOverOn5XX(boolean failOverOn5XX) {
		this.failOverOn5XX = failOverOn5XX;
	}
	
	public boolean isAdjustHostHeader() {
		return adjustHostHeader;
	}
	
	@MCAttribute
	public void setAdjustHostHeader(boolean adjustHostHeader) {
		this.adjustHostHeader = adjustHostHeader;
	}
	
	public HttpClientConfiguration getHttpClientConfig() {
		return httpClientConfig;
	}
	
	@MCChildElement
	public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
		this.httpClientConfig = httpClientConfig;
	}
}
