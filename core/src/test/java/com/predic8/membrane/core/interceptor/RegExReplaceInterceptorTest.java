/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;

public class RegExReplaceInterceptorTest {

	private Router router;
	
	@Before
	public void setUp() throws Exception {
		router = Router.init("src/test/resources/regex-monitor-beans.xml");
		Rule serverRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3009), "predic8.de", 80);
		router.getRuleManager().addProxyAndOpenPortIfNew(serverRule);
		router.init();
	}
	
	@Test
	public void testReplace() throws Exception {
		HttpClient client = new HttpClient();
		
		GetMethod method = new GetMethod("http://localhost:3009");
		method.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		method.setRequestHeader(Header.SOAP_ACTION, "");
		
		assertEquals(200, client.executeMethod(method));
		
		assertTrue(new String(method.getResponseBody()).contains("Membrane RegEx Replacement Is Cool"));
	}

	@After
	public void tearDown() throws Exception {
		router.shutdownNoWait();
	}	
}
