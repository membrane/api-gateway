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

import org.junit.platform.suite.api.*;

@Suite
@SelectPackages({"com.predic8.membrane.core"})
/**
 * @TODO Fis:
 * - com.predic8.membrane.core.interceptor.soap.SampleSoapInterceptorTest
 * - com.predic8.membrane.core.interceptor.opentelemetry.OpenTelemetryInterceptorTest
 * - com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptorIntegrationTest   // Rewrite as UnitTest with sampleSOAPService
 * Still in use?
 * - com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2InterceptorTest
 * - com.predic8.membrane.core.interceptor.shadowing.ShadowingInterceptorTest
 */
@ExcludeClassNamePatterns({
		"com.predic8.membrane.core.interceptor.soap.ws_addressing.*",
		"com.predic8.membrane.core.interceptor.soap.SampleSoapInterceptorTest",
		"com.predic8.membrane.core.interceptor.opentelemetry.OpenTelemetryInterceptorTest",
		"com.predic8.membrane.core.interceptor.session.SessionInterceptorTest",
		"com.predic8.membrane.core.interceptor.balancer.NodeOnlineCheckerTest",
		"com.predic8.membrane.core.interceptor.tunnel.WebsocketStompTest",
		"com.predic8.membrane.core.interceptor.rest.RESTBLZServiceIntegrationTest",
		"com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2InterceptorTest",
		"com.predic8.membrane.core.interceptor.shadowing.ShadowingInterceptorTest",
		"com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptorIntegrationTest",
		//
		// From includes, weren't run before!
		// TODO: Fix or delete
		"com.predic8.membrane.core.transport.http.ConnectionTest",
		"com.predic8.membrane.core.util.MemcachedConnectorTest"

})
public class UnitTests {
    /*
	 * @BeforeClass public static void forbidScreenOutput() { PrintStream ps =
	 * new PrintStream(new OutputStream() {
	 *
	 * @Override public void write(int b) throws IOException { throw new
	 * RuntimeException("this test uses stdout"); } }); System.setOut(ps);
	 * System.setErr(ps); }
	 */
}