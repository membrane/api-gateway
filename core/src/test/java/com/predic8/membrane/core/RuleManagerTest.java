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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static com.predic8.membrane.test.TestUtil.assembleExchange;
import static org.junit.jupiter.api.Assertions.*;

public class RuleManagerTest {

	RuleManager manager;
	ProxyRule proxy3013;
	ServiceProxy forwardBlz;
	ServiceProxy forwardBlzPOST;
	InternalProxy internal;

	Router router;

	@BeforeEach
	public void setUp() throws Exception{
		manager = new RuleManager();
		router = new TestRouter();
		router.init();
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
		router.stop();
	}

	@Test
	@DisplayName("An API with a key that is already registered is not added")
	void duplicateKeyIsNotAdded() throws Exception {
		var duplicate = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3014), "thomas-bayer.com", 80);
		duplicate.init(router);

		manager.addProxyAndOpenPortIfNew(duplicate);

		assertEquals(4, manager.getRules().size());
		assertSame(forwardBlz, manager.getRules().get(1));
	}

	@Test
	@DisplayName("Internal proxies are told apart by identity, not by their key")
	void internalProxiesAreNotDeduplicated() {
		var second = new InternalProxy();
		second.setName("invoice");
		second.init(router);

		manager.addProxy(second, RuleManager.RuleDefinitionSource.MANUAL);

		assertEquals(5, manager.getRules().size());
	}

	/**
	 * APIs sharing a port are told apart by their test expression, which only exists after init().
	 * Deduplicating while the configuration is parsed would therefore throw them away, see
	 * distribution/tutorials/soap/75-Routing-by-SOAPAction.yaml.
	 */
	@Test
	@DisplayName("APIs on one port that only differ in their test expression all stay registered")
	void apisDiscriminatedByTestExpressionAreKept(@TempDir Path tempDir) throws Exception {
		Path config = tempDir.resolve("apis.yaml");
		Files.writeString(config, """
				api:
				  name: get
				  port: 2000
				  test: header.SOAPAction == 'get'
				  flow:
				    - return:
				        status: 200
				---
				api:
				  name: create
				  port: 2000
				  test: header.SOAPAction == 'create'
				  flow:
				    - return:
				        status: 200
				---
				api:
				  name: fallback
				  port: 2000
				  flow:
				    - return:
				        status: 404
				""");

		DefaultRouter yamlRouter = new DefaultRouter();
		try {
			loadIntoRouter(yamlRouter, config.toString());
			yamlRouter.start();

			assertEquals(List.of("get", "create", "fallback"),
					yamlRouter.getRuleManager().getRules().stream().map(p -> p.getName()).toList());
		} finally {
			yamlRouter.stop();
		}
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
		Exchange exc = get("/ignored").buildExchange();
		exc.getDestinations().add("internal://unknown");
		assertInstanceOf(NullProxy.class, manager.getMatchingRule(exc));
	}

	@Test
	void internal() throws URISyntaxException {
		assertEquals("order", manager.getMatchingRule(get("internal://order").buildExchange()).getName());
	}

	@Test
	void internalWithPath() throws URISyntaxException {
		assertEquals("order", manager.getMatchingRule(get("internal://order/path").buildExchange()).getName());
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