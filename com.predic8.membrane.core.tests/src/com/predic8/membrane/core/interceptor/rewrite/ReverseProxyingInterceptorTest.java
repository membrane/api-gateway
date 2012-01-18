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
		Assert.assertEquals("/local", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "/local"));
	}

	@Test
	public void sameServer() throws Exception {
		Assert.assertEquals("http://membrane:2000/bar", getRewrittenRedirectionLocation("membrane", 2000, "http://target/foo", "http://target/bar"));
	}


	/**
	 * Lets the ReverseProxyingInterceptor handle a fake Exchange and returns the rewritten "Location" header.
	 */
	private String getRewrittenRedirectionLocation(String hostHeader, int port, String destURI, String redirectionURI) throws Exception {
		Exchange exc = createExchange(hostHeader, port, destURI, redirectionURI);
		Assert.assertEquals(Outcome.CONTINUE, rp.handleResponse(exc));
		return exc.getResponse().getHeader().getFirstValue(Header.LOCATION);
	}
	
	/**
	 * Creates a fake exchange which simulates received redirect by the server. 
	 */
	private Exchange createExchange(String hostHeader, int port, String destURI, String redirectionURI) {
		Exchange exc = new Exchange();
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
		req.setUri(destURI);
		Header header = new Header();
		if (hostHeader != null)
			header.setHost(hostHeader);
		req.setHeader(header);
		exc.setRequest(req);
		Response res = Response.redirect(redirectionURI, false).build();
		exc.setResponse(res);
		return exc;
	}

}
