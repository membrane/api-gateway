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
package com.predic8.membrane.core.interceptor;

import static com.predic8.membrane.test.AssertUtils.postAndAssert;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class LimitInterceptorTest {
	
	private HttpRouter router;

	@Before
	public void before() throws Exception {
		router = new HttpRouter();

		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3026), "", -1);
		
		LimitInterceptor wi = new LimitInterceptor();
		wi.setMaxBodyLength(10);
		wi.setFlow(Flow.REQUEST);
		sp2.getInterceptors().add(wi);

		WebServerInterceptor wi2 = new WebServerInterceptor();
		wi2.setDocBase("classpath:");
		wi2.init();
		sp2.getInterceptors().add(wi2);

		router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
		router.init();
	}
	
	@After
	public void after() throws IOException {
		router.shutdownNoWait();
	}

	@Test
	public void small() throws ClientProtocolException, IOException {
		postAndAssert(200, "http://localhost:3026/articleRequest.xml", "aaaaa");
	}

	@Test
	public void large() throws ClientProtocolException, IOException {
		postAndAssert(400, "http://localhost:3026/articleRequest.xml", "aaaaaaaaaaaaaa");
	}

}
