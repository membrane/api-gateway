package com.predic8.membrane.core.interceptor;

import static junit.framework.Assert.assertEquals;

import java.net.URL;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.util.MessageUtil;
public class DispatchingInterceptorTest {

	private DispatchingInterceptor dispatcher;
	
	private Exchange exc;
	
	@Before
	public void setUp() throws Exception {
		dispatcher = new DispatchingInterceptor();
		exc = new Exchange();
	}
	
	@Test
	public void testForwardingRule() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/axis2/services/BLZService?wsdl"));
		exc.setRule(getForwardingRule());
		
		assertEquals(Outcome.CONTINUE, dispatcher.handleRequest(exc));
		
		URL url = new URL(exc.getDestinations().get(0));
		assertEquals(80, url.getPort());
		assertEquals("thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}
	
	@Test
	public void testProxyRuleHttp() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("http://www.thomas-bayer.com:80/axis2/services/BLZService?wsdl"));
		exc.setRule(getProxyrRule());
		
		assertEquals(Outcome.CONTINUE, dispatcher.handleRequest(exc));
		
		URL url = new URL(exc.getDestinations().get(0));
		
		assertEquals(80, url.getPort());
		assertEquals("www.thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}
	
	@Test
	public void testProxyRuleHttps() throws Exception {
		
	}
	
	private ForwardingRule getForwardingRule() {		
		return new ForwardingRule(new ForwardingRuleKey("localhost", ".*", ".*", 8080), "thomas-bayer.com", 80);
	}
	
	private ProxyRule getProxyrRule() {
		return new ProxyRule(new ProxyRuleKey(9000));
	}
	
}
