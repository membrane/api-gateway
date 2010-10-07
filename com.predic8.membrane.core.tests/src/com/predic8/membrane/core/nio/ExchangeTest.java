/* Copyright 2005-2010 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.nio;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.transport.nio.ChannelState;
import com.predic8.membrane.core.transport.nio.NioHttpTransport;

/**
 * 
 */
public class ExchangeTest extends NioTestBase {

	NioHttpTransport transport;

	@Before
	public void setUp() throws Exception {
		transport = new NioHttpTransport();
	}

	@After
	public void tearDown() throws Exception {
		transport.shutdown();
	}

	@Test(timeout = 30100)
	public void testDurchstich() throws Exception {
		Exchange exc = new Exchange();
		exc.transport = transport;
		transport.startAccepting(exc);
		Thread.sleep(30000);
		if(exc.backendSide.getWriteState() != ChannelState.CLOSED)
			fail();
	}

}
