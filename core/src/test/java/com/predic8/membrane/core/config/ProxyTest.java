/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;


public class ProxyTest {

	private ProxyConfiguration proxy;
	
	@Before
	public void setUp() throws Exception {
		proxy = new ProxyConfiguration();
		proxy.setUsername("predic8");
		proxy.setPassword("secret");
	}
	
	@Test
	public void testGetCredentials() throws Exception {
		String credentials = proxy.getCredentials();
		assertEquals("Basic cHJlZGljODpzZWNyZXQ=", credentials);
	}
	
}
