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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class ReverseProxyingInterceptorTest {
	final ReverseProxyingInterceptor rp = new ReverseProxyingInterceptor();

	@Test
	public void localRedirect() {
		rp.init(new Router());
		// invalid by spec, redirection location should not be rewritten
		assertEquals("/local", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "/local"));
	}

	@Test
	public void sameServer() {
		// same server, redirection location should be rewritten
		// (whether ":80" actually occurs the final string does not matter)
		assertEquals("http://membrane/bar", getRewrittenRedirectionLocation("membrane", 80, "http://target/foo", "http://target/bar"));
	}

	@Test
	public void sameServerNonStdPort() {
		// same server, redirection location should be rewritten
		assertEquals("http://membrane:2000/bar", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "http://target/bar"));
	}

	@Test
	public void differentPort() {
		// different port, redirection location should not be rewritten
		assertEquals("http://membrane:2001/bar", getRewrittenRedirectionLocation("membrane", 80, "http://membrane:2000/foo", "http://membrane:2001/bar"));
	}

	@Test
	public void differentServer() {
		// different server, redirection location should not be rewritten
		assertEquals("http://target2/bar", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "http://target2/bar"));
	}


	/**
	 * Lets the ReverseProxyingInterceptor handle a fake Exchange and returns the rewritten "Location" header.
	 */
	private String getRewrittenRedirectionLocation(String requestHostHeader, int port, String requestURI, String redirectionURI) {
		Exchange exc = createExchange(requestHostHeader, null, port, requestURI, redirectionURI);
		exc.setProxy(new ServiceProxy());
		assertEquals(CONTINUE, rp.handleResponse(exc));
		return exc.getResponse().getHeader().getFirstValue(LOCATION);
	}

	@Test
	public void localDestination() throws Exception {
		// invalid by spec, redirection location should not be rewritten
		assertEquals("/local", getRewrittenDestination("membrane", "/local", 2000, "/foo", "http", 80));
	}

	@Test
	public void sameServerDestination() throws Exception {
		// same server, redirection location should be rewritten
		// (whether ":80" actually occurs the final string does not matter)
		assertEquals("http://target/bar", getRewrittenDestination("membrane", "http://membrane/bar", 80, "/foo", "http", 80));
	}

	@Test
	public void sameServerNonStdPortDestination() throws Exception {
		// same server, redirection location should be rewritten
		assertEquals("https://target:81/bar", getRewrittenDestination("membrane:2000", "http://membrane:2000/bar", 2000, "/foo", "https", 81));
	}

	@Test
	public void differentPortDestination() throws Exception {
		// different port, redirection location should not be rewritten
		assertEquals("http://membrane:2001/bar", getRewrittenDestination("membrane", "http://membrane:2001/bar", 80, "/foo", "http", 80));
	}

	@Test
	public void differentServerDestination() throws Exception {
		// different server, redirection location should not be rewritten
		assertEquals("http://target2/bar", getRewrittenDestination("membrane", "http://target2/bar", 2000, "/foo", "http", 80));
	}


	/**
	 * Lets the ReverseProxyingInterceptor handle a fake Exchange and returns the rewritten "Destination" header.
	 */
	private String getRewrittenDestination(String requestHostHeader, String requestDestinationHeader, int port, String requestURI, String targetScheme, int targetPort) throws Exception {
		Exchange exc = createExchange(requestHostHeader, requestDestinationHeader, port, requestURI, null);
		exc.setProxy(new ServiceProxy());
		String url = new URL(targetScheme, "target", targetPort, exc.getRequest().getUri()).toString();
		exc.getDestinations().add(url);
		assertEquals(CONTINUE, rp.handleRequest(exc));
		return exc.getRequest().getHeader().getFirstValue(DESTINATION);
	}

	/**
	 * Creates a fake exchange which simulates a received redirect by the server.
	 */
	private Exchange createExchange(String requestHostHeader, String requestDestinationHeader, int port, String requestURI, String redirectionURI) {
		Exchange exc = new Exchange(new FakeHttpHandler(port));
		exc.setProxy(new ServiceProxy());
		Request req = new Request();
		req.setUri(requestURI);
		Header header = new Header();
		if (requestHostHeader != null)
			header.setHost(requestHostHeader);
		if (requestDestinationHeader != null)
			header.add(DESTINATION, requestDestinationHeader);
		req.setHeader(header);
		exc.setRequest(req);
		if (redirectionURI != null) {
			Response res = Response.redirect(redirectionURI, false).build();
			exc.setResponse(res);
			exc.getDestinations().add(requestURI);
		}
		return exc;
	}
}
