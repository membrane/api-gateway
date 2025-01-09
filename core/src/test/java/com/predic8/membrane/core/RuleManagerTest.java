/* Copyright 2009, 2012, 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.test.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class RuleManagerTest {

	RuleManager manager;
	ProxyRule proxy3013;
	ServiceProxy forwardBlz;
	ServiceProxy forwardBlzPOST;
	InternalProxy internal;

	MockRouter router;

	@BeforeEach
	public void setUp() throws Exception{
		manager = new RuleManager();
		router = new MockRouter();
		manager.setRouter(router);
		proxy3013 = new ProxyRule(new ProxyRuleKey(3013));
		manager.addProxyAndOpenPortIfNew(proxy3013);

		forwardBlz = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3014), "thomas-bayer.com", 80);
		forwardBlz.init(router);

		forwardBlzPOST = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3015), "thomas-bayer.com", 80);
		forwardBlzPOST.init(router);

		internal = new InternalProxy();
		internal.setName("order");
		internal.init(router);

		manager.addProxyAndOpenPortIfNew(forwardBlz);
		manager.addProxyAndOpenPortIfNew(forwardBlzPOST);
		manager.addProxy(internal, RuleManager.RuleDefinitionSource.MANUAL);
	}

	@AfterEach
	public void tearDown() {
		router.shutdown();
	}

	@Test
	void getRules() {
		assertEquals(4, manager.getRules().size());
	}

	@Test
	void exists() {
		assertTrue(manager.exists(proxy3013.getKey()));
	}

	@Test
	void getMatchingRuleForwardBlz() throws UnknownHostException {
		assertEquals(forwardBlz, manager.getMatchingRule(assembleExchange("localhost", "POST", "/axis2/services/blzservice", "1.1", 3014, "127.0.0.1")));
	}

	@Test
	void getMatchingRuleForwardBlzPOST() throws UnknownHostException {
		assertEquals(forwardBlz, manager.getMatchingRule(assembleExchange("localhost", "POST", "/axis2/services/blzservice", "1.1", 3014, "127.0.0.1")));
	}

	@Test
	void internalUnknown() throws URISyntaxException {
		Exchange exc = Request.get("/ignored").buildExchange();
		exc.getDestinations().add("service://unknown");
		assertInstanceOf(NullProxy.class, manager.getMatchingRule(exc));
	}

	@Test
	void internal() throws URISyntaxException {
		assertEquals("order", manager.getMatchingRule(Request.get("service://order").buildExchange()).getName());
	}

	@Test
	void internalWithPath() throws URISyntaxException {
		assertEquals("order", manager.getMatchingRule(Request.get("service://order/path").buildExchange()).getName());
	}

	@Test
	void testRemoveRule() {
		manager.removeRule(proxy3013);
		assertEquals(3, manager.getRules().size());
		assertFalse(manager.getRules().contains(proxy3013));
	}

	@Test
	void removeAllRules() {
		manager.removeAllRules();
		assertTrue(manager.getRules().isEmpty());
	}

	@Test
	void isAnyRuleWithPort() {
		assertFalse(manager.isAnyRuleWithPort(1234));
		assertTrue(manager.isAnyRuleWithPort(3013));
		assertTrue(manager.isAnyRuleWithPort(3014));
		assertTrue(manager.isAnyRuleWithPort(3015));
	}
}