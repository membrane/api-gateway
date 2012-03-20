/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.text.SimpleDateFormat;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpTransport;

public class CoachDBInterceptor extends AbstractInterceptor {
	
	private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");
	
	public static final String STATUS_CODE = "status-code";
	
	public static final String TIME = "time";
	
	public static final String RULE = "rule";
	
	public static final String METHOD = "method";
	
	public static final String PATH = "path";
	
	public static final String CLIENT = "client";

	public static final String SERVER = "server";
	
	public static final String REQUEST_CONTENT_TYPE = "req-cont-type";
	
	public static final String REQUEST_CONTENT_LENGTH = "req-cont-length";
	
	public static final String RESPONSE_CONTENT_TYPE = "resp-cont-type";
	
	public static final String RESPONSE_CONTENT_LENGTH = "resp-cont-length";
	
	private HttpClient client = new HttpClient();
	
	private String targetHost;
	
	private int targetPort;
	
	private Rule rule;
	

	public CoachDBInterceptor() {
		
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		
		StringBuffer buffer = new StringBuffer();
		buffer.append("{");
		

		appendToBuffer(buffer, STATUS_CODE, Integer.toString(exc.getResponse().getStatusCode()));
		
		synchronized(DATE_FORMATTER) {
			appendToBuffer(buffer, TIME, exc.getTime() == null ? Constants.UNKNOWN : DATE_FORMATTER.format(exc.getTime().getTime()));
		}
		
		appendToBuffer(buffer, RULE, exc.getRule().toString());
		
		appendToBuffer(buffer, METHOD, exc.getRequest().getMethod());
		
		appendToBuffer(buffer, PATH, exc.getOriginalRequestUri());
		
		appendToBuffer(buffer, CLIENT, (String) exc.getSourceHostname());
		
		appendToBuffer(buffer, SERVER, exc.getServer());
		
		appendToBuffer(buffer, REQUEST_CONTENT_TYPE, exc.getRequestContentType());
		
		appendToBuffer(buffer, REQUEST_CONTENT_LENGTH, "" +exc.getRequestContentLength());
		
		appendToBuffer(buffer, RESPONSE_CONTENT_TYPE, exc.getResponseContentType());
		
		appendToBuffer(buffer, RESPONSE_CONTENT_LENGTH, "" + exc.getResponseContentLength());
		
		buffer.append("}");
		
//		
//		Session session = new Session(targetHost, targetPort);
//		session.getDatabase("membrane");
		
		
		//doCall(buffer.toString());
		
		Exchange exchange = new Exchange();
		exchange.setRule(getRule());
		exchange.setRequest(createRequest(buffer));
		
		exchange.setProperty(HttpTransport.HEADER_HOST, exchange.getRequest().getHeader().getHost());
		exchange.setOriginalRequestUri(exchange.getRequest().getUri());
		exchange.getRequest().getHeader().setHost(((ServiceProxy) exchange.getRule()).getTargetHost() + ":" + ((ServiceProxy) exchange.getRule()).getTargetPort());
		
		try {
			client.call(exchange);
			// TODO
				
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		return Outcome.CONTINUE;
	}

	private Request createRequest(StringBuffer buffer) {
		Request request = new Request();
		request.setMethod(Request.METHOD_PUT);
		request.setVersion("1.1");
		request.setUri("http://" + targetHost + ":" + targetPort + "/membrane/1");
		Header header = new Header();
		header.setAccept("application/json");
		header.setContentType(MimeType.JSON);
		
		request.setHeader(header);
		request.setBodyContent(buffer.toString().getBytes(Constants.UTF_8_CHARSET));
		return request;
	}
	
	private void appendToBuffer(StringBuffer buffer, String key, String value) {
		buffer.append(key);
		buffer.append(":");
		buffer.append(value);
		buffer.append(",");
	}

	public void setTargetHost(String targetHost) {
		this.targetHost = targetHost;
	}

	public void setTargetPort(int targetPort) {
		this.targetPort = targetPort;
	}
		
	private Rule getRule() {
		if (rule == null) {
			rule = new ServiceProxy(new ServiceProxyKey("localhost", Request.METHOD_POST, ".*", 4100), targetHost, targetPort);
		}
		return rule;
	}
	

}
