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
package com.predic8.membrane.integration;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpTransport;


public class CouchDBTest extends TestCase {

	private String targetHost = "192.168.2.131";
	private int targetPort = 5984;
	
	private HttpClient client = new HttpClient();
	
	private Rule rule;
	
	private int currentId = 25;
	
	@Before
	public void setUp() throws Exception {
		
	}
	
	@Test
	public void testCreateTable() throws Exception {
		
		Exchange exchange = new Exchange();
		exchange.setRule(getRule());
		exchange.setRequest(createRequest("http://" + targetHost + ":" + targetPort + "/tblmembrane/", null));
		
		exchange.setProperty(HttpTransport.HEADER_HOST, exchange.getRequest().getHeader().getHost());
		exchange.setOriginalRequestUri(exchange.getRequest().getUri());
		exchange.getRequest().getHeader().setHost(((ServiceProxy) exchange.getRule()).getTargetHost() + ":" + ((ServiceProxy) exchange.getRule()).getTargetPort());
		
		try {
			Response resp = client.call(exchange);
			System.err.println("Status code of DB response: " + resp.getStatusCode());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
	}
	
	@Test
	public void testPutDocument() throws Exception {
		
		Exchange exchange = new Exchange();
		exchange.setRule(getRule());
		exchange.setRequest(createRequest("http://" + targetHost + ":" + targetPort + "/tblmembrane/" + currentId, "{\"alpha\":\"thomas\"}"));
		
		exchange.setProperty(HttpTransport.HEADER_HOST, exchange.getRequest().getHeader().getHost());
		exchange.setOriginalRequestUri(exchange.getRequest().getUri());
		exchange.getRequest().getHeader().setHost(((ServiceProxy) exchange.getRule()).getTargetHost() + ":" + ((ServiceProxy) exchange.getRule()).getTargetPort());
		
		try {
			Response resp = client.call(exchange);
			System.err.println("Status code of DB response: " + resp.getStatusCode());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		currentId ++;
	}
	
	private Request createRequest(String url, String content) {
		Request request = new Request();
		request.setMethod(Request.METHOD_PUT);
		request.setVersion("1.1");
		request.setUri(url);
		Header header = new Header();
		header.setAccept("application/json");
		header.setContentType("application/json");
		
		request.setHeader(header);
		if (content != null)
			request.setBodyContent(content.getBytes());
		return request;
	}
	
	private Rule getRule() {
		if (rule == null) {
			rule = new ServiceProxy(new ServiceProxyKey("localhost", Request.METHOD_POST, ".*", 4100), targetHost, targetPort);
		}
		return rule;
	}
	
}
