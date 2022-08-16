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

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.resolver.HTTPSchemaResolver;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

import static org.junit.Assert.*;

import static org.mockito.Mockito.*;

@SuppressWarnings("javadoc")
public class HttpTransportTest {

	private HttpClient httpClient = mock(HttpClient.class);
	private HTTPSchemaResolver httpSchemaResolver = mock(HTTPSchemaResolver.class);
	private ResolverMap resolverMap = mock(ResolverMap.class);
	private SSLProvider sslProvider = mock(SSLProvider.class);
	private RuleManager ruleManager = mock(RuleManager.class);
	private Router router = mock(Router.class);
	private HttpTransport transport;

	@Before
	public void before() throws Exception {
		when(httpSchemaResolver.getHttpClient(null)).thenReturn(httpClient);
		when(resolverMap.getHTTPSchemaResolver()).thenReturn(httpSchemaResolver);
		when(router.getResolverMap()).thenReturn(resolverMap);
		when(router.getRuleManager()).thenReturn(ruleManager);

		transport = new HttpTransport();
		transport.init(router);
	}

	@After
	public void after() throws IOException {
		transport.closeAll();
	}

	@Test
	public final void testCloseAllBoolean() throws IOException {
		transport.closeAll(false);
		transport.closeAll(true);
	}

	@Test
	public final void testOpenPortOK_NoSSL() throws IOException {
		transport.openPort("localhost", 80, null);
		transport.openPort("127.0.0.1", 80, null);
	}

	@Test
	public final void testOpenPortOK_SSL() throws IOException {
		transport.openPort("localhost", 80, sslProvider);
		transport.openPort("127.0.0.1", 80, sslProvider);
	}

	@Test
	public final void testOpenPortErr_SSL() throws IOException {
		transport.openPort("localhost", 80, sslProvider);
		try {
			transport.openPort("127.0.0.1", 80, null);
			fail("Should throw RuntimeException");
		} catch (RuntimeException e) {
			assertEquals("Lister thread on '/127.0.0.1:80' should use the same SSL config", e.getMessage());
		}
	}

	@Test
	public final void testOpenPortErr_0() throws IOException {
		transport.openPort("localhost", 80, sslProvider);
		try {
			transport.openPort(null, 80, sslProvider);
			fail("Should throw RuntimeException");
		} catch (RuntimeException e) {
			assertEquals("Conflict with listening on the same net interfaces ['*:80', 'localhost/127.0.0.1:80']",
					e.getMessage());
		}
	}

	@Test
	public final void testOpenPortErr_1() throws IOException {
		transport.openPort(null, 80, sslProvider);
		try {
			transport.openPort("127.0.0.1", 80, sslProvider);
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
		transport.openPort("localhost", 80, sslProvider);
		transport.openPort("127.0.0.1", 80, sslProvider);
		transport.closePort(new IpPort("192.1.1.1", 80));
		transport.closePort(new IpPort("localhost", 80));
	}

	@Test
	public final void testSetGetCoreThreadPoolSize() {
		assertEquals(20, transport.getCoreThreadPoolSize());
		transport.setCoreThreadPoolSize(10);
		assertEquals(10, transport.getCoreThreadPoolSize());
	}

	@Test
	public final void testSetGetMaxThreadPoolSize() {
		assertEquals(Integer.MAX_VALUE, transport.getMaxThreadPoolSize());
		transport.setMaxThreadPoolSize(33);
		assertEquals(33, transport.getMaxThreadPoolSize());
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
