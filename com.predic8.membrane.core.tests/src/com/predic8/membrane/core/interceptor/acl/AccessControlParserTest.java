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

	
	public static final String FILE_NAME = "resources/acl.xml";
	
	public static final String PATH_SERVICE_1 = "/axis2/services";
	
	public static final String PATH_SERVICE_2 = "/crm/kundenservice";
	
	public static final String SERVICE_1_IP_1 = "192.168.23.131";
	
	public static final String SERVICE_1_HOSTNAME_1 = "*.predic8.de";
	
	public static final String SERVICE_2_IP_1 = "192.168.23.*";
	
	public static final String SERVICE_2_IP_2 = "192.168.11.*";
	
	public static final String SERVICE_2_HOSTNAME_1 = "pc1.predic8.de";
	
	public static final String SERVICE_2_HOSTNAME_2 = "pc1.predic8.com";
	
	public static final String SERVICE_2_HOSTNAME_3 = "pc1.xy.com";
	
	private AccessControl accessControl;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		accessControl = new AccessControlInterceptor().parse(FILE_NAME);
	}
	
	public void testServiceCount() throws Exception {
		assertEquals(2, accessControl.getResources().size());
	}
	
	public void testService1() throws Exception {
		List<Resource> services = accessControl.getResources();
		Resource service = services.get(0);		
		assertTrue(service.matches(PATH_SERVICE_1));
		
		assertEquals(1, service.getIpAddresses().size());
		assertEquals(1, service.getHostnames().size());
		
		assertEquals(SERVICE_1_IP_1, service.getIpAddresses().get(0).toString());
		//TODO 
		//assertEquals(SERVICE_1_HOSTNAME_1, service.getHostNames().get(0).toString());
	}
	
	public void testService2() throws Exception {
		List<Resource> services = accessControl.getResources();
		Resource service = services.get(1);
		assertTrue(service.matches(PATH_SERVICE_2));
		
		assertEquals(2, service.getIpAddresses().size());
		assertEquals(3, service.getHostnames().size());
		
		//TODO
		//assertEquals(SERVICE_2_IP_1, service.getIpAddresses().get(0));
		//assertEquals(SERVICE_2_HOSTNAME_1, service.getHostNames().get(0));
		
		//assertEquals(SERVICE_2_IP_2, service.getIpAddresses().get(1));
		//assertEquals(SERVICE_2_HOSTNAME_2, service.getHostNames().get(1));
		
		//assertEquals(SERVICE_2_HOSTNAME_3, service.getHostNames().get(2));
		
		
	}
	
}
