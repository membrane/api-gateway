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

import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class HttpTransportTest {

	private final SSLProvider sslProvider = mock(SSLProvider.class);
	private HttpTransport transport;

	@BeforeEach
	void before() {
		var router = new DummyTestRouter();
		transport = router.getTransport();
	}

	@AfterEach
	void after() {
		transport.closeAll();
	}

	@Test
	void testCloseAllBoolean() {
		transport.closeAll(false);
		transport.closeAll(true);
	}

	@Test
	void testOpenPortOK_NoSSL() throws IOException {
		transport.openPort("localhost", 3000, null);
		transport.openPort("127.0.0.1", 3001, null);
	}

	@Test
	void testOpenPortOK_SSL() throws IOException {
		transport.openPort("localhost", 80, sslProvider);
		transport.openPort("127.0.0.1", 80, sslProvider);
	}

	@Test
	void testOpenPortErr_SSL() throws IOException {
		transport.openPort("localhost", 80, sslProvider);
		try {
			transport.openPort("127.0.0.1", 80, null);
			fail("Should throw RuntimeException");
		} catch (RuntimeException e) {
			assertEquals("Lister thread on '/127.0.0.1:80' should use the same SSL config", e.getMessage());
		}
	}

	@Test
	void testOpenPortErr_0() throws IOException {
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
	void testOpenPortErr_1() throws IOException {
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
	void testIsOpeningPorts() {
		assertTrue(transport.isOpeningPorts());
	}

	@Test
	void testClosePort() throws IOException {
		transport.openPort("localhost", 80, sslProvider);
		transport.openPort("127.0.0.1", 80, sslProvider);
		transport.closePort(new IpPort("192.1.1.1", 80));
		transport.closePort(new IpPort("localhost", 80));
	}

	@Test
	void testGetExecutorService() {
		assertNotNull(transport.getExecutorService());
	}

	@Test
	void testSetGetSocketTimeout() {
		assertEquals(30000, transport.getSocketTimeout());
		transport.setSocketTimeout(12);
		assertEquals(12, transport.getSocketTimeout());
	}

	@Test
	void testSetIsTcpNoDelay() {
		assertTrue(transport.isTcpNoDelay());
		transport.setTcpNoDelay(false);
		assertFalse(transport.isTcpNoDelay());
	}

	@Test
	void testSetGetForceSocketCloseOnHotDeployAfter() {
		assertEquals(30000, transport.getForceSocketCloseOnHotDeployAfter());
		transport.setForceSocketCloseOnHotDeployAfter(13);
		assertEquals(13, transport.getForceSocketCloseOnHotDeployAfter());
	}
}
