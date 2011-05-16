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

import junit.framework.TestCase;

public class AccessControlParserTest extends TestCase {

	public static final String FILE_NAME = "resources/acl/acl.xml";
	
	public static final String RESOURCE_URI_1 = "/axis2/services";
	
	public static final String RESOURCE_URI_2 = "/crm/kundenservice";
	
	private AccessControl accessControl;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		accessControl = new AccessControlInterceptor().parse(FILE_NAME);
	}
	
	public void testResourceCount() throws Exception {
		assertEquals(2, accessControl.getResources().size());
	}
	
	public void testResource1() throws Exception {
		List<Resource> resources = accessControl.getResources();
		Resource resource = resources.get(0);		
		assertTrue(resource.matches(RESOURCE_URI_1));
		
		assertEquals(2, resource.getIpAddresses().size());
		assertEquals(2, resource.getHostnames().size());
		
		assertTrue(resource.getIpAddresses().get(0).matches("192.168.23.131"));
		assertTrue(resource.getHostnames().get(0).matches("predic8.de"));
	}
	
	public void testResource2() throws Exception {
		List<Resource> resources = accessControl.getResources();
		Resource resource = resources.get(1);
		assertTrue(resource.matches(RESOURCE_URI_2));
		
		assertEquals(2, resource.getIpAddresses().size());
		assertEquals(3, resource.getHostnames().size());
		
		assertTrue(resource.getIpAddresses().get(0).matches("192.168.23.12"));
		assertTrue(resource.getIpAddresses().get(1).matches("192.168.11.2"));
		assertTrue(resource.getHostnames().get(0).matches("pc1.predic8.de"));
		assertTrue(resource.getHostnames().get(1).matches("pc1.predic8.com"));
		assertTrue(resource.getHostnames().get(2).matches("pc1.xy.com"));
	}
	
}
