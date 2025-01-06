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


import com.predic8.membrane.core.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionTest {

	Connection conLocalhost;
	Connection con127_0_0_1;

	HttpRouter router;

	@BeforeEach
	public void setUp() throws Exception {

		ServiceProxy proxy2000 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), "predic8.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(proxy2000);

		conLocalhost = Connection.open("localhost", 2000, null, null, 30000);
		con127_0_0_1 = Connection.open("127.0.0.1", 2000, null, null, 30000);
	}

	@AfterEach
	public void tearDown() throws Exception {
		conLocalhost.close();
		con127_0_0_1.close();
		assertTrue(conLocalhost.isClosed());
		assertTrue(con127_0_0_1.isClosed());

		router.shutdown();
	}


	@Test
	public void testIsSame() {
		assertTrue(conLocalhost.isSame("127.0.0.1", 2000));
		assertTrue(con127_0_0_1.isSame("localhost", 2000));
	}
}
