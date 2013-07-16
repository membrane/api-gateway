/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import static junit.framework.Assert.assertEquals;

import java.io.IOException;
import java.util.HashSet;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;

public class HttpKeepAliveTest {

	private HashSet<Integer> set; // tracks the hashcodes of all connections used
	private HttpRouter service1;
	private ServiceProxy sp1;
	
	@Before
	public void setUp() throws Exception {
		set = new HashSet<Integer>();
		
		service1 = new HttpRouter();
		sp1 = new ServiceProxy(new ServiceProxyKey("localhost",
				"POST", ".*", 2003), "thomas-bayer.com", 80);
		sp1.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				exc.getRequest().readBody();
				exc.setResponse(Response.ok("OK.").build());
				set.add(((HttpServerHandler)exc.getHandler()).getSrcOut().hashCode());
				return Outcome.RETURN;
			}
		});
		service1.getRuleManager().addProxyAndOpenPortIfNew(sp1);
		service1.init();
	}

	@After
	public void tearDown() throws Exception {
		service1.shutdownNoWait();
	}

	private HttpClient createHttpClient(int defaultKeepAliveTimeout) {
		HttpClientConfiguration configuration = new HttpClientConfiguration();
		ConnectionConfiguration connection = new ConnectionConfiguration();
		connection.setKeepAliveTimeout(defaultKeepAliveTimeout);
		configuration.setConnection(connection);
		HttpClient client = new HttpClient(configuration);
		return client;
	}

	private Exchange createExchange() throws IOException {
		return new Request.Builder().
			url("http://localhost:2003/axis2/services/BLZService").
			method(Request.METHOD_POST).
			header(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8).
			header(Header.SOAP_ACTION, "").
			body(IOUtils.toByteArray(this.getClass().getResourceAsStream("/getBank.xml"))).
			buildExchange();
	}
	
	private int issueRequest(HttpClient client) throws IOException, Exception {
		Exchange exchange = createExchange();
		Response response = client.call(exchange).getResponse();
		response.readBody();
		return response.getStatusCode();
	}

	@Test
	public void testKeepAlive() throws Exception {
		HttpClient client = new HttpClient();

		assertEquals(200, issueRequest(client));
		assertEquals(200, issueRequest(client));

		assertEquals(1, set.size());
	}

	@Test
	public void testTimeoutDefault() throws Exception {
		HttpClient client = createHttpClient(1000);
		
		sp1.getInterceptors().add(0, new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) throws Exception {
				exc.getResponse().getHeader().add(Header.KEEP_ALIVE, "max=2");
				return Outcome.CONTINUE;
			}
		});

		assertEquals(200, issueRequest(client));
		assertEquals(1, set.size());
		
		Thread.sleep(1500);

		assertEquals(200, issueRequest(client));
		assertEquals(2, set.size());
	}

	@Test
	public void testTimeoutCustom() throws Exception {
		HttpClient client = createHttpClient(1000);
		
		sp1.getInterceptors().add(0, new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) throws Exception {
				exc.getResponse().getHeader().add(Header.KEEP_ALIVE, "timeout=1,max=2");
				return Outcome.CONTINUE;
			}
		});

		assertEquals(200, issueRequest(client));
		assertEquals(1, set.size());
		
		Thread.sleep(1500);

		assertEquals(200, issueRequest(client));
		assertEquals(2, set.size());

		assertEquals(200, issueRequest(client));
		assertEquals(2, set.size());
		
		assertEquals(200, issueRequest(client));
		assertEquals(3, set.size());
	}

	@Test
	public void testMaxParameter() throws Exception {
		HttpClient client = new HttpClient();
		
		sp1.getInterceptors().add(0, new AbstractInterceptor() {
			@Override
			public Outcome handleResponse(Exchange exc) throws Exception {
				exc.getResponse().getHeader().add(Header.KEEP_ALIVE, "max=2");
				return Outcome.CONTINUE;
			}
		});

		assertEquals(200, issueRequest(client));
		assertEquals(200, issueRequest(client));
		assertEquals(1, set.size());

		assertEquals(200, issueRequest(client));
		assertEquals(2, set.size());
		
		assertEquals(200, issueRequest(client));
		assertEquals(2, set.size());

		assertEquals(200, issueRequest(client));
		assertEquals(3, set.size());
	}

}
