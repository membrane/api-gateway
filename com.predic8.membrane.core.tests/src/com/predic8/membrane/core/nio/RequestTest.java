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

import java.nio.channels.SocketChannel;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.nio.*;

import junit.framework.TestCase;

/**
 * 
 */
public class RequestTest extends TestCase {

	public static final int PORT = 2000;
	
	private NioHttpTransport transport;

	@Before
	public void setUp() throws Exception {
		transport = new NioHttpTransport();
		transport.openReceiving(new NioAcceptor() {
			
			public int getListenPort() {
				return PORT;
			}
			
			public NioConnectionHandler accept(SocketChannel connection) {
				return new NioDummyRequestDecoder();
			}
		});
	}

	@Override
	protected void tearDown() throws Exception {
		transport.closeAll();
	}

	@Test
	public void testGet() throws Exception {
		HttpClient client = new HttpClient();
		GetMethod get = new GetMethod("http://localhost:" + PORT + "/foo/bar");
		
		int statuscode = client.executeMethod(get);
		System.out.println("Status Code: " + statuscode);
		System.out.println(get.getStatusLine());
		for(Header h: get.getResponseHeaders()){
			System.out.print(h);
		}
	}
}
