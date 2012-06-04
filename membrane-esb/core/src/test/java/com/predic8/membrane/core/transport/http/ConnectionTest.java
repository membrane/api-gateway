/* Copyright 2012 predic8 GmbH, www.predic8.com

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
		
		conLocalhost = Connection.open(InetAddress.getByName("localhost"), 2000, null, null);
		con127_0_0_1 = Connection.open(InetAddress.getByAddress(LOCALHOST_IP), 2000, null, null);
	}

	@After
	public void tearDown() throws Exception {
		conLocalhost.close();
		con127_0_0_1.close();
		assertTrue(conLocalhost.isClosed());
		assertTrue(con127_0_0_1.isClosed());
		
		router.shutdown();
	}

	
	@Test
	public void testIsSame() throws Exception {
		assertTrue(conLocalhost.isSame(InetAddress.getByAddress(LOCALHOST_IP), 2000));
		assertTrue(con127_0_0_1.isSame(InetAddress.getByName("localhost"), 2000));
	}
}
