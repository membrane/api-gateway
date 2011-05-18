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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import org.junit.Test;

public class HostColonPortTest {

	@Test
	public void testIllegalInput() throws Exception {
		try {
			new HostColonPort("predic8.com80");
			fail("Illegal argument exception expected but thrown none.");
		} catch (IllegalArgumentException e) {
			// Test OK
		}
	}
	
	@Test
	public void testGetHost() throws Exception {
		HostColonPort hcp = new HostColonPort("predic8.com:80");
		assertEquals("predic8.com", hcp.host);
	}

	@Test
	public void testGetPort() throws Exception {
		HostColonPort hcp = new HostColonPort("predic8.com:80");
		assertEquals(80, hcp.port);
	}

	
}
