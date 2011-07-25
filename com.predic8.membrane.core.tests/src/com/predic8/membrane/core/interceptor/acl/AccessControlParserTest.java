/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.acl;

import java.util.List;

import org.junit.Test;

import junit.framework.TestCase;

public class AccessControlParserTest extends TestCase {

	public static final String FILE_NAME = "resources/acl/acl.xml";
	
	public static final String RESOURCE_URI_1 = "/axis2/services";
	
	public static final String RESOURCE_URI_2 = "/crm/kundenservice";
	
	private List<Resource> resources;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		resources = new AccessControlInterceptor().parse(FILE_NAME).getResources();
	}
	
	@Test
	public void testResourceCount() throws Exception {
		assertEquals(3, resources.size());
	}
	
	/*@Test
	public void testAxis2ResourceClientsSize() throws Exception {
		assertEquals(4, resources.get(0).getClientAddresses().size());
	}
	
	@Test
	public void testAxis2ResourcePattern() throws Exception {
		assertEquals("^/axis2/.*$", resources.get(0).getPattern());
	}
	
	@Test
	public void testAxis2ResourceClientList() throws Exception {
		assertEquals("^192\\.168\\.23\\.131$", resources.get(0).getClientAddresses().get(0).toString());
		assertEquals("^predic8\\.de$", resources.get(0).getClientAddresses().get(1).toString());
		assertEquals("^sami$", resources.get(0).getClientAddresses().get(2).toString());
		assertEquals("^127\\.0\\.0\\.1$", resources.get(0).getClientAddresses().get(3).toString());
	}
	
	@Test
	public void testCrmResourceClientsSize() throws Exception {
		assertEquals(5, resources.get(1).getClientAddresses().size());
	}
	
	@Test
	public void testCrmResourcePattern() throws Exception {
		assertEquals("^/crm/.*$", resources.get(1).getPattern());
	}
	
	@Test
	public void testCrmResourceClientList() throws Exception {
		assertEquals("^192\\.168\\.23\\..*$", resources.get(1).getClientAddresses().get(0).toString());
		assertEquals("^pc1\\.predic8\\.de$", resources.get(1).getClientAddresses().get(1).toString());
		assertEquals("^192\\.168\\.11\\.2$", resources.get(1).getClientAddresses().get(2).toString());
		assertEquals("^pc1\\.predic8\\.com$", resources.get(1).getClientAddresses().get(3).toString());
		assertEquals("^pc1\\.xy\\.com$", resources.get(1).getClientAddresses().get(4).toString());
	}
	
	@Test
	public void testAbcResourceClientsSize() throws Exception {
		assertEquals(1, resources.get(2).getClientAddresses().size());
	}
	
	@Test
	public void testAbcResourcePattern() throws Exception {
		assertEquals("^/abc/.*$", resources.get(2).getPattern());
	}
	
	@Test
	public void testAbcResourceClientList() throws Exception {
		assertEquals("^.*$", resources.get(2).getClientAddresses().get(0).toString());
	}*/

}
