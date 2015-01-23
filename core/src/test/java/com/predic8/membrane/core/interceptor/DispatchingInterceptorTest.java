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
package com.predic8.membrane.core.interceptor;

import static org.junit.Assert.assertEquals;

import java.net.URL;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.util.MessageUtil;
public class DispatchingInterceptorTest {

	private DispatchingInterceptor dispatcher;
	
	private Exchange exc;
	
	@Before
	public void setUp() throws Exception {
		dispatcher = new DispatchingInterceptor();
		exc = new Exchange(null);
	}
	
	@Test
	public void testServiceProxy() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/axis2/services/BLZService?wsdl"));
		exc.setRule(getServiceProxy());
		
		assertEquals(Outcome.CONTINUE, dispatcher.handleRequest(exc));
		
		URL url = new URL(exc.getDestinations().get(0));
		assertEquals(80, url.getPort());
		assertEquals("thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}

	@Test
	public void testServiceProxyWithAbsoluteURI() throws Exception {
        exc.setRequest(MessageUtil.getGetRequest("http://www.example.com/axis2/services/BLZService?wsdl"));
        exc.setRule(getServiceProxy());

        assertEquals(Outcome.CONTINUE, dispatcher.handleRequest(exc));

        URL url = new URL(exc.getDestinations().get(0));
        assertEquals(80, url.getPort());
        assertEquals("thomas-bayer.com", url.getHost());
        assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}

	@Test
	public void testProxyRuleHttp() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("http://www.thomas-bayer.com:80/axis2/services/BLZService?wsdl"));
		exc.setRule(getProxyRule());
		
		assertEquals(Outcome.CONTINUE, dispatcher.handleRequest(exc));
		
		URL url = new URL(exc.getDestinations().get(0));
		
		assertEquals(80, url.getPort());
		assertEquals("www.thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}

	@Test
	public void testProxyRuleHttps() throws Exception {
		
	}
	
	private ServiceProxy getServiceProxy() {		
		return new ServiceProxy(new ServiceProxyKey("localhost", ".*", ".*", 3011), "thomas-bayer.com", 80);
	}
	
	private ProxyRule getProxyRule() {
		return new ProxyRule(new ProxyRuleKey(3090));
	}
}