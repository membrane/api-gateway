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
package com.predic8.membrane.core.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.SpringInterceptor;
import com.predic8.membrane.core.rules.Rule;

public class ReadRulesWithInterceptorsConfigurationTest {

	private Router router;

	private List<Rule> rules;

	@Before
	public void setUp() throws Exception {
		router = Router.init("src/test/resources/ref.proxies.xml");
		rules = router.getRuleManager().getRules();
	}

	@Test
	public void testRulesSize() throws Exception {
		Assert.assertEquals(2, rules.size());
	}

	@Test
	public void testRuleInterceptorSize() throws Exception {
		Rule rule = rules.get(0);
		assertEquals(1, rule.getInterceptors().size());
	}

	@Test
	public void testRuleInterceptorsHaveRouterReference() throws Exception {
		List<Interceptor> interceptors = rules.get(0).getInterceptors();
		for (Interceptor itsp : interceptors) {
			assertNotNull(itsp.getRouter());
		}
	}

	@Test
	public void testRuleInterceptorIDs() throws Exception {
		List<Interceptor> interceptors = rules.get(0).getInterceptors();
		assertEquals("accessControlInterceptor", ((SpringInterceptor) interceptors.get(0)).getRefId());
	}

	@Test
	public void testRuleInterceptorDisplayNames() throws Exception {
		List<Interceptor> interceptors = rules.get(0).getInterceptors();
		assertEquals("Access Control List Interceptor", interceptors.get(0).getDisplayName());
	}

	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

}
