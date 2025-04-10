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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

public class BoundConnectionTest {

	HttpRouter router;
	volatile long connectionHash = 0;

	@BeforeEach
	public void setUp() throws Exception {
		router = new HttpRouter();
		ServiceProxy sp1 = new ServiceProxy(new ServiceProxyKey("*",
				"*", ".*", 3021), "localhost", 3022);
		router.getRuleManager().addProxyAndOpenPortIfNew(sp1);
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*",
				"*", ".*", 3022), "", -1);
		sp2.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) {
                try {
                    exc.getRequest().readBody();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                exc.setResponse(Response.ok("OK.").build());
				connectionHash = ((HttpServerHandler)exc.getHandler()).getSrcOut().hashCode();
				return Outcome.RETURN;
			}
		});
		router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
		router.init();
	}

	@AfterEach
	public void tearDown() {
		router.shutdown();
	}


	private Request createRequest(boolean includeAuthorizationHeader) {
		Request r = new Request();
		r.setMethod("GET");
		r.setUri("/");
		r.setVersion("1.1");
		if (includeAuthorizationHeader)
			r.getHeader().add(Header.AUTHORIZATION, "NTLM abcd");
		r.getHeader().add(Header.CONTENT_LENGTH, "0");
		r.getHeader().add(Header.HOST, "localhost");
		return r;
	}

	private void doExchange(Connection c, boolean includeAuthorizationHeader) throws IOException, EndOfStreamException {
		createRequest(includeAuthorizationHeader).write(c.out, true);
		Response r = new Response();
		r.read(c.in, true);
	}

	@Test
	public void testBinding() throws Exception {
		Connection c = null, c2 = null;
		try {
			c = Connection.open("localhost", 3021, null, null, 30000);
			doExchange(c, true); // this opens a bound targetConnection

			long authenticatedConnectionHash = connectionHash;
			assertTrue(authenticatedConnectionHash != 0);

			c2 = Connection.open("localhost", 3021, null, null, 30000);
			doExchange(c2, true); // this should not reuse the same targetConnection

			long authenticatedConnection2Hash = connectionHash;
			assertTrue(authenticatedConnection2Hash != authenticatedConnectionHash);

			doExchange(c, true); // this reuses the bound targetConnection1
            assertEquals(connectionHash, authenticatedConnectionHash);

			doExchange(c, false); // this reuses the bound targetConnection1 (now even without the "Authorization" header)
            assertEquals(connectionHash, authenticatedConnectionHash);

			doExchange(c, false); // this reuses the bound targetConnection1 (now even without the "Authorization" header)
            assertEquals(connectionHash, authenticatedConnectionHash);

			doExchange(c2, false);  // this reuses the bound targetConnection2
            assertEquals(connectionHash, authenticatedConnection2Hash);
		} finally {
			if (c != null)
				c.close();
			if (c2 != null)
				c2.close();
		}
	}
}
