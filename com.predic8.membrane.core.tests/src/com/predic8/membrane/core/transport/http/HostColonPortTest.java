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
