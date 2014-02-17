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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.TerminateException;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.util.HttpUtil;

public class Exchange extends AbstractExchange {

	public static final String HTTP_SERVLET_REQUEST = "HttpServletRequest";

	private static Log log = LogFactory.getLog(Exchange.class.getName());

	private AbstractHttpHandler handler;

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

	public Connection getTargetConnection() {
		return targetConnection;
	}

	public void setTargetConnection(Connection con) {
		targetConnection = con;
	}

	public void collectStatistics() {
		rule.collectStatisticsFrom(this);
	}

	/**
	 * Returns the relative original URI.
	 * 
	 * "original" meaning "as recieved by Membrane's transport".
	 * 
	 * To be used, for example, when generating self-referring web pages.
	 */
	public String getRequestURI() {
		if (HttpUtil.isAbsoluteURI(getOriginalRequestUri())) {
			try {
				return new URL(getOriginalRequestUri()).getFile();
			} catch (MalformedURLException e) {
				throw new RuntimeException("Request has a malformed URI: "
						+ getOriginalRequestUri(), e);
			}
		}
		return getOriginalRequestUri();
	}

	public Outcome echo() throws IOException {
		ResponseBuilder builder = Response.ok();
		byte[] content = getRequest().getBody().getContent();
		builder.body(content);
		String contentType = getRequest().getHeader().getContentType();
		if (contentType != null)
			builder.header(Header.CONTENT_TYPE, contentType);
		String contentEncoding = getRequest().getHeader().getContentEncoding();
		if (contentEncoding != null)
			builder.header(Header.CONTENT_ENCODING, contentEncoding);
		setResponse(builder.build());
		return Outcome.RETURN;
	}

	public Map<String, String> getStringProperties() {
		Map<String, String> map = new HashMap<String, String>();

		for (Map.Entry<String, Object> e : properties.entrySet()) {
			if (e.getValue() instanceof String) {
				map.put(e.getKey(), (String) e.getValue());
			}
		}
		return map;
	}
	
	@Override
	public void detach() {
		super.detach();
		handler = null;
	}
	
	public boolean canKeepConnectionAlive() {
		return getRequest().isKeepAlive() && getResponse().isKeepAlive();
	}
}
