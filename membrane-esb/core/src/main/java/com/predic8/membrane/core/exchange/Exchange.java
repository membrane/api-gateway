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

package com.predic8.membrane.core.exchange;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.TerminateException;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.util.HttpUtil;

public class Exchange extends AbstractExchange {

	private static Log log = LogFactory.getLog(Exchange.class.getName());

	private final AbstractHttpHandler handler;

	private String originalHostHeader = "";

	private Connection targetConnection;

	public Exchange(AbstractHttpHandler handler) {
		this.handler = handler;

	}

	/**
	 * For HttpResendRunnable
	 * 
	 * @param original
	 */
	public Exchange(Exchange original, AbstractHttpHandler handler) {
		super(original);
		this.handler = handler;
		originalHostHeader = original.originalHostHeader;
	}

	public AbstractHttpHandler getHandler() {
		return handler;
	}

	public String getOriginalHostHeaderHost() {
		return originalHostHeader.replaceFirst(":.*", "");
	}

	public void blockRequestIfNeeded() throws TerminateException {
		if (getRule().isBlockRequest()) {
			synchronized (getRequest()) {
				setStopped();
				block(getRequest());
			}
		}
	}

	public void blockResponseIfNeeded() throws TerminateException {
		if (getRule().isBlockResponse()) {
			synchronized (getResponse()) {
				setStopped();
				block(getResponse());
			}
		}
	}

	public void block(Message msg) throws TerminateException {
		try {
			log.debug("Message thread waits");
			msg.wait();
			log.debug("Message thread received notify");
			if (isForcedToStop())
				throw new TerminateException("Force the exchange to stop.");
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	public String getOriginalHostHeaderPort() {
		return originalHostHeader.replaceFirst(".*:", "");
	}

	public void setOriginalHostHeader(String hostHeader) {
		originalHostHeader = hostHeader;
	}

	@Override
	public void setRequest(Request req) {
		super.setRequest(req);
		setOriginalHostHeader(req.getHeader().getHost());
	}

	public ServiceProxyKey getServiceProxyKey() {
		return new ServiceProxyKey(request.getHeader().getHost(),
				request.getMethod(), request.getUri(), handler.getLocalPort());
	}

	public Connection getTargetConnection() {
		return targetConnection;
	}

	public void setTargetConnection(Connection con) {
		targetConnection = con;
	}

	public void collectStatistics() {
		rule.collectStatisticsFrom(this);
	}
	
	public String getRequestURI() throws MalformedURLException {
		if (HttpUtil.isAbsoluteURI(getOriginalRequestUri())) 
			return new URL(getOriginalRequestUri()).getFile();
		return getOriginalRequestUri();
	}

	public Outcome echo() throws IOException {
		ResponseBuilder builder = Response.ok();
		byte[] content = getRequest().getBody().getContent();
		builder.body(content);
		builder.header(Header.CONTENT_LENGTH, "" + content.length);
		String contentType = getRequest().getHeader().getContentType();
		if (contentType != null)
			builder.header(Header.CONTENT_TYPE, contentType);
		setResponse(builder.build());
		return Outcome.RETURN;
	}
}
