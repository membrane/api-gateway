/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.GlobalInterceptor;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class HttpTransportTest {

	private final HTTPSchemaResolver httpSchemaResolver = mock(HTTPSchemaResolver.class);
	private final ResolverMap resolverMap = mock(ResolverMap.class);
	private final SSLProvider sslProvider = mock(SSLProvider.class);
	private final RuleManager ruleManager = mock(RuleManager.class);
	private final Router router = mock(Router.class);
	private final ExchangeStore exchangeStore = mock(ExchangeStore.class);
	private final Statistics statistics = new Statistics();
	private HttpTransport transport;
	private final GlobalInterceptor globalInterceptor = new GlobalInterceptor();

	@BeforeEach
	public void before() throws Exception {
		when(resolverMap.getHTTPSchemaResolver()).thenReturn(httpSchemaResolver);
		when(router.getResolverMap()).thenReturn(resolverMap);
		when(router.getRuleManager()).thenReturn(ruleManager);
		when(router.getExchangeStore()).thenReturn(exchangeStore);
		when(router.getGlobalInterceptor()).thenReturn(globalInterceptor);
		when(router.getHttpClientFactory()).thenReturn(new HttpClientFactory(null));
		when(router.getStatistics()).thenReturn(statistics);

		transport = new HttpTransport();
		transport.init(router);
	}

	@AfterEach
	public void after() {
		transport.closeAll();
	}

	@Test
	public final void testCloseAllBoolean() {
		transport.closeAll(false);
		transport.closeAll(true);
	}

	@Test
	public final void testOpenPortOK_NoSSL() throws IOException {
		transport.openPort("localhost", 3000, null, null);
		transport.openPort("127.0.0.1", 3001, null, null);
	}

	@Test
	public final void testOpenPortOK_SSL() throws IOException {
		transport.openPort("localhost", 80, sslProvider, null);
		transport.openPort("127.0.0.1", 80, sslProvider, null);
	}

	@Test
	public final void testOpenPortErr_SSL() throws IOException {
		transport.openPort("localhost", 80, sslProvider, null);
		try {
			transport.openPort("127.0.0.1", 80, null, null);
			fail("Should throw RuntimeException");
		} catch (RuntimeException e) {
			assertEquals("Lister thread on '/127.0.0.1:80' should use the same SSL config", e.getMessage());
		}
	}

	@Test
	public final void testOpenPortErr_0() throws IOException {
		transport.openPort("localhost", 80, sslProvider, null);
		try {
			transport.openPort(null, 80, sslProvider, null);
			fail("Should throw RuntimeException");
		} catch (RuntimeException e) {
			assertEquals("Conflict with listening on the same net interfaces ['*:80', 'localhost/127.0.0.1:80']",
					e.getMessage());
		}
	}

	@Test
	public final void testOpenPortErr_1() throws IOException {
		transport.openPort(null, 80, sslProvider, null);
		try {
			transport.openPort("127.0.0.1", 80, sslProvider, null);
			fail("Should throw RuntimeException");
		} catch (RuntimeException e) {
			assertEquals("Conflict with listening on the same net interfaces ['/127.0.0.1:80', '*:80']",
						e.getMessage());
		}
	}

	@Test
	public final void testIsOpeningPorts() {
		assertTrue(transport.isOpeningPorts());
	}

	@Test
	public final void testClosePort() throws IOException {
		transport.openPort("localhost", 80, sslProvider, null);
		transport.openPort("127.0.0.1", 80, sslProvider, null);
		transport.closePort(new IpPort("192.1.1.1", 80));
		transport.closePort(new IpPort("localhost", 80));
	}

	@Test
	public final void testGetExecutorService() {
		assertNotNull(transport.getExecutorService());
	}

	@Test
	public final void testSetGetSocketTimeout() {
		assertEquals(30000, transport.getSocketTimeout());
		transport.setSocketTimeout(12);
		assertEquals(12, transport.getSocketTimeout());
	}

	@Test
	public final void testSetIsTcpNoDelay() {
		assertTrue(transport.isTcpNoDelay());
		transport.setTcpNoDelay(false);
		assertFalse(transport.isTcpNoDelay());
	}

	@Test
	public final void testSetGetForceSocketCloseOnHotDeployAfter() {
		assertEquals(30000, transport.getForceSocketCloseOnHotDeployAfter());
		transport.setForceSocketCloseOnHotDeployAfter(13);
		assertEquals(13, transport.getForceSocketCloseOnHotDeployAfter());
	}

}
