package com.predic8.membrane.core.interceptor.acl;

import java.util.List;

import junit.framework.TestCase;

public class AccessControlParserTest extends TestCase {

	
	private AccessControlParser reader = new AccessControlParser();
	
	public static final String FILE_NAME = "resources/acl.xml";
	
	public static final String PATH_SERVICE_1 = "/axis2/*";
	
	public static final String PATH_SERVICE_2 = "/crm/*";
	
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
		accessControl = reader.read(FILE_NAME);
	}
	
	public void testServiceCount() throws Exception {
		assertEquals(2, accessControl.getServices().size());
	}
	
	public void testService1() throws Exception {
		List<Service> services = accessControl.getServices();
		Service service = services.get(0);
		assertTrue(service.matches(PATH_SERVICE_1));
		
		assertEquals(1, service.getIpAddresses().size());
		assertEquals(1, service.getHostNames().size());
		
		assertEquals(SERVICE_1_IP_1, service.getIpAddresses().get(0));
		assertEquals(SERVICE_1_HOSTNAME_1, service.getHostNames().get(0));
	}
	
	public void testService2() throws Exception {
		List<Service> services = accessControl.getServices();
		Service service = services.get(1);
		assertTrue(service.matches(PATH_SERVICE_2));
		
		assertEquals(2, service.getIpAddresses().size());
		assertEquals(3, service.getHostNames().size());
		
		assertEquals(SERVICE_2_IP_1, service.getIpAddresses().get(0));
		assertEquals(SERVICE_2_HOSTNAME_1, service.getHostNames().get(0));
		
		assertEquals(SERVICE_2_IP_2, service.getIpAddresses().get(1));
		assertEquals(SERVICE_2_HOSTNAME_2, service.getHostNames().get(1));
		
		assertEquals(SERVICE_2_HOSTNAME_3, service.getHostNames().get(2));
		
		
	}
	
}
