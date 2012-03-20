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

package com.predic8.membrane.core.interceptor.xmlprotection;

import static junit.framework.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.MessageUtil;

public class XMLProtectionInterceptorTest {
	private Exchange exc;
	private XMLProtectionInterceptor interceptor;

	private Rule getRule() {		
		return new ServiceProxy(new ServiceProxyKey("localhost", ".*", ".*", 3011), "thomas-bayer.com", 80);
	}

	@Before
	public void setUp() throws Exception {
		exc = new Exchange();
		exc.setRequest(MessageUtil.getGetRequest("/axis2/services/BLZService"));
		exc.setOriginalHostHeader("thomas-bayer.com:80");
		exc.setRule(getRule());
		
		interceptor = new XMLProtectionInterceptor();
	}

	private void runOn(String resource, boolean expectSuccess) throws Exception {
		exc.getRequest().getHeader().setContentType("application/xml");
		exc.getRequest().setBodyContent(ByteUtil.getByteArrayData(this.getClass().getResourceAsStream(resource)));
		Outcome outcome = interceptor.handleRequest(exc);
		assertEquals(expectSuccess ? Outcome.CONTINUE : Outcome.ABORT, outcome);
	}
	
	@Test
	public void testInvariant() throws Exception {
		runOn("/customer.xml", true);
	}

	@Test
	public void testNotWellformed() throws Exception {
		runOn("/xml/not-wellformed.xml", false);
	}


}
