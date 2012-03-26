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

package com.predic8.membrane.core.interceptor.rewrite;

import javax.xml.stream.XMLStreamReader;

import junit.framework.Assert;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.AbstractProxy;
import com.predic8.membrane.core.rules.AbstractRuleKey;

public class ReverseProxyingInterceptorTest {
	ReverseProxyingInterceptor rp = new ReverseProxyingInterceptor();

	@Test
	public void localRedirect() throws Exception {
		// invalid by spec, redirection location should not be rewritten
		Assert.assertEquals("/local", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "/local"));
	}

	@Test
	public void sameServer() throws Exception {
		// same server, redirection location should be rewritten
		// (whether ":80" actually occurs the final string does not matter)
		Assert.assertEquals("http://membrane:80/bar", getRewrittenRedirectionLocation("membrane", 80, "http://target/foo", "http://target/bar"));
	}

	@Test
	public void sameServerNonStdPort() throws Exception {
		// same server, redirection location should be rewritten
		Assert.assertEquals("http://membrane:2000/bar", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "http://target/bar"));
	}

	@Test
	public void differentPort() throws Exception {
		// different port, redirection location should not be rewritten
		Assert.assertEquals("http://membrane:2001/bar", getRewrittenRedirectionLocation("membrane", 80, "http://membrane:2000/foo", "http://membrane:2001/bar"));
	}

	@Test
	public void differentServer() throws Exception {
		// different server, redirection location should not be rewritten
		Assert.assertEquals("http://target2/bar", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "http://target2/bar"));
	}


	/**
	 * Lets the ReverseProxyingInterceptor handle a fake Exchange and returns the rewritten "Location" header.
	 */
	private String getRewrittenRedirectionLocation(String requestHostHeader, int port, String requestURI, String redirectionURI) throws Exception {
		Exchange exc = createExchange(requestHostHeader, port, requestURI, redirectionURI);
		Assert.assertEquals(Outcome.CONTINUE, rp.handleResponse(exc));
		return exc.getResponse().getHeader().getFirstValue(Header.LOCATION);
	}
	
	/**
	 * Creates a fake exchange which simulates a received redirect by the server. 
	 */
	private Exchange createExchange(String requestHostHeader, int port, String requestURI, String redirectionURI) {
		Exchange exc = new Exchange(null);
		exc.setRule(new AbstractProxy(new AbstractRuleKey(port) {}){
			@Override
			protected void parseKeyAttributes(XMLStreamReader token) {
				throw new NotImplementedException();
			}

			@Override
			protected AbstractProxy getNewInstance() {
				throw new NotImplementedException();
			}});
		Request req = new Request();
		req.setUri(requestURI);
		Header header = new Header();
		if (requestHostHeader != null)
			header.setHost(requestHostHeader);
		req.setHeader(header);
		exc.setRequest(req);
		Response res = Response.redirect(redirectionURI, false).build();
		exc.setResponse(res);
		return exc;
	}

}
