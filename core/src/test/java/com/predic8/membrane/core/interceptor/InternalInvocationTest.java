/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.junit.jupiter.api.*;

import java.io.*;

public class InternalInvocationTest {

	private Router router;

	@BeforeEach
	public void setUp() throws Exception {
		router = Router.init("classpath:/internal-invocation/proxies.xml");
		MockInterceptor.clear();
	}

	@Test
	void testFullChain() throws Exception {
		callService(3028);

		MockInterceptor.assertContent(
				new String[] { "Mock1", "Mock2", "Mock3", "Mock4", "Mock5", "Mock6"},
				new String[] { "Mock6", "Mock5", "Mock4", "Mock3", "Mock2", "Mock1" },
				new String[] { });
	}

	@Test
	void returnedChain() throws Exception {
		callService(3029);

		MockInterceptor.assertContent(
				new String[] { "Mock1", "Mock2", "Mock3"},
				new String[] { "Mock3", "Mock2", "Mock1" },
				new String[] { });
	}

	@AfterEach
	public void tearDown() throws Exception {
		router.shutdown();
	}

	private void callService(int port) throws IOException {
		new HttpClient().executeMethod(new GetMethod("http://localhost:"+port));
	}

}
