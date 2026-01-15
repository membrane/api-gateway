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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.RouterXmlBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyRuleTest {

	private static Router router;
	private static ProxyRule proxy;

	@BeforeAll
	public static void setUp() {
		router = RouterXmlBootstrap.initByXML("src/test/resources/proxy-rules-test-monitor-beans.xml");
		proxy = new ProxyRule(new ProxyRuleKey(8888));
		proxy.setName("Rule 1");
		// TODO: this is not possible anymore rule.setInboundTLS(true);
		proxy.setFlow(getFlow());

	}

	@AfterAll
	public static void tearDown() {
		router.stop();
	}

	@Test
	public void testRule() {

		assertEquals(8888, proxy.getKey().getPort());
		assertEquals("Rule 1", proxy.getName());
		//TODO: see above assertEquals(true, rule.isInboundTLS());
		assertNull(proxy.getSslOutboundContext());

		List<Interceptor> inters = proxy.getFlow();
		assertFalse(inters.isEmpty());
        assertEquals(1, inters.size());
	}

	private static List<Interceptor> getFlow() {
		List<Interceptor> interceptors = new ArrayList<>();
		Interceptor balancer = new LoadBalancingInterceptor();
		interceptors.add(balancer);
		return interceptors;
	}

}
