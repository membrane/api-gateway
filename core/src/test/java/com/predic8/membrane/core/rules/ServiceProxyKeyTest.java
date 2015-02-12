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
package com.predic8.membrane.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.http.Request;


public class ServiceProxyKeyTest {

	
	@Before
	public void setUp() throws Exception {
			
	}
	
	@Test
	public void testSimpleConstructor() throws Exception {
		ServiceProxyKey key = new ServiceProxyKey(3000);
		assertEquals(3000, key.getPort());
		assertEquals("*", key.getMethod());
		assertEquals("*", key.getHost());
		assertNull(key.getPath());
		assertTrue(key.isPathRegExp());
		assertTrue(key.isHostWildcard());
		assertFalse(key.isUsePathPattern());
	}
	
	@Test
	public void testNoRegExpMatchesPath() throws Exception {
		ServiceProxyKey key = new ServiceProxyKey("localhost", Request.METHOD_POST, "/axis2/services", 3000);
		key.setPathRegExp(false);
		
		assertTrue(key.matchesPath("/axis2/services/bla/other"));
		assertFalse(key.matchesPath("/service/we"));
	}
	
	@Test
	public void testRegularExpressionMatchesPath() throws Exception {
		ServiceProxyKey key = new ServiceProxyKey("localhost", Request.METHOD_POST, ".*FooService", 3000);
		assertTrue(key.matchesPath("/axis2/services/FooService"));
		assertFalse(key.matchesPath("/axis2/services/FooService/Bla")); //???
	}
	
	@Test
	public void testRegularExpressionMatchesPathAnyURI() throws Exception {
		ServiceProxyKey key = new ServiceProxyKey("localhost", Request.METHOD_POST, ".*", 3000);
		assertTrue(key.matchesPath("/axis2/services/FooService"));
		assertTrue(key.matchesPath("/axis2/services/FooService/Bla"));
	}
	
	@Test
	public void testHostMatch() {
		testHostMatch("localhost", "localhost");
		testHostMatch("foo.predic8.de", "foo.predic8.de");
		testHostMatch(" foo.predic8.de ", "foo.predic8.de");
		testHostMatch("foo.predic8.de", "foo.predic8.de:80");
		testHostMatch("foo.predic8.de *.predic8.de", "foo.predic8.de");
		testHostMatch("foo.predic8.de *.predic8.de", "bar.predic8.de");
		testHostMatch("foo.predic8.de bar.predic8.de", "baz.predic8.de", false);
		testHostMatch("*.predic8.de", "foo.predic8.de");
		testHostMatch("a b c", "c");
	}

	private void testHostMatch(String hostArg, String hostHeader) {
		testHostMatch(hostArg, hostHeader, true);
	}
	
	private void testHostMatch(String hostArg, String hostHeader, boolean result) {
		assertEquals(new ServiceProxyKey(hostArg, "GET", null, 80).matchesHostHeader(hostHeader), result);
	}
}
