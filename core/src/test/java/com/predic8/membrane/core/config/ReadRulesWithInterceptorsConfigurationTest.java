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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ReadRulesWithInterceptorsConfigurationTest {

	private static Router router;

	private static List<Proxy> proxies;

	@BeforeAll
	public static void setUp() {
		router = Router.init("src/test/resources/ref.proxies.xml");
		proxies = router.getRuleManager().getRules();
	}

	@Test
	public void testRulesSize() {
		assertEquals(2, proxies.size());
	}

	@Test
	public void testRuleInterceptorSize() {
		Proxy proxy = proxies.get(0);
		assertEquals(1, proxy.getInterceptors().size());
	}

	@Test
	public void testRuleInterceptorsHaveRouterReference() {
		List<Interceptor> interceptors = proxies.get(0).getInterceptors();
		for (Interceptor itsp : interceptors) {
			assertNotNull(itsp.getRouter());
		}
	}

	@Test
	public void testRuleInterceptorIDs() {
		List<Interceptor> interceptors = proxies.getFirst().getInterceptors();
		assertEquals("accessControlInterceptor", ((SpringInterceptor) interceptors.getFirst()).getRefId());
	}

	@Test
	public void testRuleInterceptorDisplayNames() {
		List<Interceptor> interceptors = proxies.getFirst().getInterceptors();
		assertEquals("Access Control List Interceptor", interceptors.getFirst().getDisplayName());
	}

	@AfterAll
	public static void tearDown() {
		router.shutdown();
	}

}
