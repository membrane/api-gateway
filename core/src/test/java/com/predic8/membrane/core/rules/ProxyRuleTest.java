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
package com.predic8.membrane.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;

public class ProxyRuleTest {

	private Router router;
	private Rule rule;
		
	@Before
	public void setUp() throws Exception {
		router = Router.init("src/test/resources/proxy-rules-test-monitor-beans.xml");
		rule = new ProxyRule(new ProxyRuleKey(8888));
		rule.setName("Rule 1");
		// TODO: this is not possible anymore rule.setInboundTLS(true);
		rule.setBlockResponse(true);
		rule.setInterceptors(getInterceptors());
		
	}
	
	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}
		
	@Test
	public void testRule() throws Exception {
		
		assertEquals(8888, rule.getKey().getPort());
		assertEquals("Rule 1", rule.getName());
		//TODO: see above assertEquals(true, rule.isInboundTLS());
		assertNull(rule.getSslOutboundContext());
		
		List<Interceptor> inters = rule.getInterceptors();
		assertFalse(inters.isEmpty());
		assertTrue(inters.size()  == 2);
		inters.get(0).getId().equals("roundRobinBalancer");
		inters.get(1).getId().equals("accessControlInterceptor");
		
		assertEquals(true, rule.isBlockResponse());
		assertFalse(rule.isBlockRequest());
	}
	
	private List<Interceptor> getInterceptors() {
		List<Interceptor> interceptors = new ArrayList<Interceptor>();
		Interceptor balancer = new LoadBalancingInterceptor();
		balancer.setId("roundRobinBalancer");
		interceptors.add(balancer);
		
		Interceptor acl = new AccessControlInterceptor();
		acl.setId("accessControlInterceptor");
		interceptors.add(acl);
		return interceptors;
	}
	
}
