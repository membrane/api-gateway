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

import javax.xml.stream.XMLStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.HttpUtil;

@MCInterceptor(name="httpClient")
public class HTTPClientInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(HTTPClientInterceptor.class.getName());

	private boolean failOverOn5XX;
	private long keepAliveTimeout = 30000;
	
	private volatile HttpClient httpClient;
	
	public HTTPClientInterceptor() {
		name="HTTPClient";
		setFlow(Flow.REQUEST);
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		super.parseAttributes(token);
		failOverOn5XX = Boolean.parseBoolean(token.getAttributeValue("", "failOverOn5XX"));
		if (token.getAttributeValue("", "keepAliveTimeout") != null)
			keepAliveTimeout = Long.parseLong(token.getAttributeValue("", "keepAliveTimeout"));
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		exc.blockRequestIfNeeded();
		
		Response targetRes = null;

		try {
			targetRes = getClient().call(exc);
			return Outcome.RETURN;
		} catch (ConnectException e) {
			targetRes = Response.interalServerError("Target " + getDestination(exc) + " is not reachable.").build();
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

	// http://en.wikipedia.org/wiki/Double-checked_locking
	private HttpClient getClient() {
		HttpClient result = httpClient;
		if (result == null)
			synchronized(this) {
				result = httpClient;
				if (result == null)
					httpClient = result = new HttpClient(router, failOverOn5XX, keepAliveTimeout);
			}
		return result;
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
	
	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}
	
	@MCAttribute
	public void setKeepAliveTimeout(long keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}
}
