package com.predic8.membrane.core.transport.http;


import static org.junit.Assert.assertTrue;

import java.net.InetAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;

public class ConnectionTest {

	private static final byte[] LOCALHOST_IP = new byte[]{ (byte)127, (byte)0, (byte)0,  (byte)1 };
	Connection conLocalhost;
	Connection con127_0_0_1;
	
	HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		
		Rule rule2000 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), "predic8.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyIfNew(rule2000);
		
		conLocalhost = Connection.open(InetAddress.getByName("localhost"), 2000, null, false);
		con127_0_0_1 = Connection.open(InetAddress.getByAddress(LOCALHOST_IP), 2000, null, false);
	}

	@After
	public void tearDown() throws Exception {
		conLocalhost.close();
		con127_0_0_1.close();
		assertTrue(conLocalhost.isClosed());
		assertTrue(con127_0_0_1.isClosed());
		
		router.getTransport().closeAll();
	}

	
	@Test
	public void testIsSame() throws Exception {
		assertTrue(conLocalhost.isSame(InetAddress.getByAddress(LOCALHOST_IP), 2000));
		assertTrue(con127_0_0_1.isSame(InetAddress.getByName("localhost"), 2000));
	}
}
