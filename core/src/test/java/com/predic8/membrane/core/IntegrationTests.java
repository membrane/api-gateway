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
package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStoreIntegrationTest;
import com.predic8.membrane.core.http.LargeBodyTest;
import com.predic8.membrane.core.rules.ProxySSLTest;
import com.predic8.membrane.integration.*;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

import com.predic8.membrane.core.config.SpringReferencesTest;
import com.predic8.membrane.core.http.MethodTest;
import com.predic8.membrane.core.interceptor.AdjustContentLengthIntegrationTest;
import com.predic8.membrane.core.interceptor.LimitInterceptorTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherTest;
import com.predic8.membrane.core.resolver.ResolverTest;
import com.predic8.membrane.core.rules.SOAPProxyIntegrationTest;
import com.predic8.membrane.core.rules.UnavailableSoapProxyTest;
import com.predic8.membrane.core.transport.ExceptionHandlingTest;
import com.predic8.membrane.core.transport.http.BoundConnectionTest;
import com.predic8.membrane.core.transport.http.IllegalCharactersInURLTest;
import com.predic8.membrane.core.transport.http.InterceptorInvocationTest;
import com.predic8.membrane.interceptor.LoadBalancingInterceptorTest;


@Suite
@SelectClasses( {
		IntegrationTestsWithoutInternet.class,
		IntegrationTestsWithInternet.class
})
public class IntegrationTests {
	/*
	@BeforeClass
	public static void forbidScreenOutput() {
		PrintStream ps = new PrintStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new RuntimeException("this test uses stdout");
			}
		});
		System.setOut(ps);
		System.setErr(ps);
	}
	 */
}
