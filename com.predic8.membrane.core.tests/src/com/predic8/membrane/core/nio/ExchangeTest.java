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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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

	@Ignore
	@Test(timeout = 30000)
	public void testDurchstich() throws Exception {
		Exchange exc = new Exchange();
		exc.transport = transport;
		transport.startAccepting(exc);
		GetMethod get = new GetMethod("http://localhost:7331/");
		HttpClient client = new HttpClient();
		int status = client.executeMethod(get);
		assertEquals(200, status);
		// Der HttpClient liest die Response schneller ein schneller als der
		// NioHttpTransport braucht, um die Events fertig zu bearbeiten. An
		// dieser Stelle wird kurz gewartet.
		Thread.sleep(200);
		assertEquals(ChannelState.CLOSED, exc.clientSide.getReadState());
		assertEquals(ChannelState.CLOSED, exc.clientSide.getWriteState());
		assertEquals(ChannelState.CLOSED, exc.backendSide.getReadState());
		assertEquals(ChannelState.CLOSED, exc.backendSide.getWriteState());
	}

}
