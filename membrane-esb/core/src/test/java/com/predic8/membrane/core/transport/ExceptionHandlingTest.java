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
package com.predic8.membrane.core.transport;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

@RunWith(Parameterized.class)
public class ExceptionHandlingTest {
		
	@Parameters
	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] { 
				{ true },
				{ false },
		});
	}

	private final boolean printStackTrace;

	public ExceptionHandlingTest(boolean printStackTrace) {
		this.printStackTrace = printStackTrace;
	}
	
	
	HttpRouter router;
	volatile long connectionHash = 0;
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
		router.getTransport().setPrintStackTrace(printStackTrace);
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*",
				"*", ".*", getPort()), "", -1);
		sp2.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				throw new Exception("secret");
			}
		});
		router.getRuleManager().addProxyIfNew(sp2);
	}

	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	private int getPort() {
		return printStackTrace ? 3022 : 3023;
	}

	@Test
	public void testStackTraces() throws Exception {
		String response = getAndAssert(500, "http://localhost:" + getPort() + "/");
		Assert.assertEquals(printStackTrace, response.contains(".java:"));
		assertContains("secret", response);
	}
	
}
