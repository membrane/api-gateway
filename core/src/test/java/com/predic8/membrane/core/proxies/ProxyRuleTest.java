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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyRuleTest {

	private static Router router;
	private static ProxyRule proxy;

	@BeforeAll
	public static void setUp() {
		router = Router.init("src/test/resources/proxy-rules-test-monitor-beans.xml");
		proxy = new ProxyRule(new ProxyRuleKey(8888));
		proxy.setName("Rule 1");
		// TODO: this is not possible anymore rule.setInboundTLS(true);
		proxy.setBlockResponse(true);
		proxy.setFlow(getFlow());

	}

	@AfterAll
	public static void tearDown() {
		router.shutdown();
	}

	@Test
	public void testRule() {

		assertEquals(8888, proxy.getKey().getPort());
		assertEquals("Rule 1", proxy.getName());
		//TODO: see above assertEquals(true, rule.isInboundTLS());
		assertNull(proxy.getSslOutboundContext());

		List<Interceptor> inters = proxy.getFlow();
		assertFalse(inters.isEmpty());
        assertEquals(2, inters.size());

        assertTrue(proxy.isBlockResponse());
		assertFalse(proxy.isBlockRequest());
	}

	private static List<Interceptor> getFlow() {
		List<Interceptor> interceptors = new ArrayList<>();
		Interceptor balancer = new LoadBalancingInterceptor();
		interceptors.add(balancer);

		Interceptor acl = new AccessControlInterceptor();
		interceptors.add(acl);
		return interceptors;
	}

}
