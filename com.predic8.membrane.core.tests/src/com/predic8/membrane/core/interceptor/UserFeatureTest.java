/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.io.IOException;
import java.util.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.*;

import com.predic8.membrane.core.Router;

public class UserFeatureTest {

	private Router router;

	List<String> requestLabels = Arrays.asList(new String[] { "Mock1", "Mock3",
			"Mock4", "Mock7" });

	List<String> responseLabels = Arrays.asList(new String[] { "Mock7",
			"Mock6", "Mock5", "Mock4", "Mock2", "Mock1" });

	@Before
	public void setUp() throws Exception {
		router = Router.init("resources/userFeature/monitor-beans.xml");
		router.getConfigurationManager().loadConfiguration(
				"resources/userFeature/proxies.xml");
	}

	@Test
	public void testInvokation() throws Exception {
		callService();
		assertEquals(requestLabels, MockInterceptor.reqLabels);
		assertEquals(responseLabels, MockInterceptor.respLabels);
	}

	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

	private void callService() throws HttpException, IOException {
		new HttpClient().executeMethod(new GetMethod("http://localhost:2001"));
	}

}
