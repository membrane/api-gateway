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

import java.util.HashSet;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class HttpKeepAliveTest {

	private HashSet<Integer> set; // tracks the hashcodes of all connections used
	private HttpRouter service1;
	
	@Before
	public void setUp() throws Exception {
		set = new HashSet<Integer>();
		
		service1 = new HttpRouter();
		ServiceProxy sp1 = new ServiceProxy(new ServiceProxyKey("localhost",
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
	}

	@After
	public void tearDown() throws Exception {
		service1.shutdownNoWait();
	}

	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod(
				"http://localhost:2003/axis2/services/BLZService");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass()
				.getResourceAsStream("/getBank.xml")));
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");

		return post;
	}

	@Test
	public void testKeepAlive() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION,
				HttpVersion.HTTP_1_1);

		assertEquals(200, client.executeMethod(getPostMethod()));
		assertEquals(200, client.executeMethod(getPostMethod()));

		assertEquals(1, set.size());
	}
}
