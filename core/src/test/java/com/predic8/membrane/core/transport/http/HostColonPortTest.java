/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

public class HostColonPortTest {

	@Test
	public void testDefaultPort() {
		HostColonPort hcp = new HostColonPort(false, "predic8.com");
		assertEquals("predic8.com", hcp.host());
		assertEquals(80, hcp.port());
	}

	@Test
	public void testGetHost() {
		assertEquals("predic8.com", new HostColonPort(false, "predic8.com:80").host());
	}

	@Test
	public void testGetPort() {
		assertEquals(80, new HostColonPort(false, "predic8.com:80").port());
	}

	@Test
	public void noNumber() {
		Assertions.assertThrowsExactly(NumberFormatException.class,() -> new HostColonPort(false,"foo:no-number"));
	}

    @Test
    void getProtocol() {
		assertEquals("http", new HostColonPort("foo",80).getProtocol());
		assertEquals("https", new HostColonPort("foo",443).getProtocol());
    }

    @Test
    void getUrl() {
		assertEquals("http://foo:80", new HostColonPort("foo",80).getUrl());
		assertEquals("https://foo:443", new HostColonPort("foo",443).getUrl());
    }
}
