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

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.predic8.membrane.core.http.MethodTest;
import com.predic8.membrane.core.interceptor.AdjustContentLengthIntegrationTest;
import com.predic8.membrane.core.interceptor.LimitInterceptorTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherTest;
import com.predic8.membrane.core.transport.ExceptionHandlingTest;
import com.predic8.membrane.core.transport.http.BoundConnectionTest;
import com.predic8.membrane.core.transport.http.InterceptorInvocationTest;
import com.predic8.membrane.integration.AccessControlInterceptorIntegrationTest;
import com.predic8.membrane.integration.Http10Test;
import com.predic8.membrane.integration.Http11Test;
import com.predic8.membrane.integration.ProxySSLConnectionMethodTest;
import com.predic8.membrane.integration.ViaProxyTest;
import com.predic8.membrane.interceptor.LoadBalancingInterceptorTest;

@RunWith(Suite.class)
@SuiteClasses({ MethodTest.class, RegExReplaceInterceptorTest.class,
		Http10Test.class, Http11Test.class,
		AccessControlInterceptorIntegrationTest.class,
		LoadBalancingInterceptorTest.class,
		REST2SOAPInterceptorIntegrationTest.class,
		InterceptorInvocationTest.class,
		BasicAuthenticationInterceptorIntegrationTest.class,
		ViaProxyTest.class, ProxySSLConnectionMethodTest.class,
		AdjustContentLengthIntegrationTest.class,
		BoundConnectionTest.class, ExceptionHandlingTest.class,
		WSDLPublisherTest.class, LimitInterceptorTest.class })
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
