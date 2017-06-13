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

import com.predic8.membrane.core.exchangestore.AbortExchangeTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.predic8.membrane.core.config.ProxyTest;
import com.predic8.membrane.core.http.BodyTest;
import com.predic8.membrane.core.http.HeaderTest;
import com.predic8.membrane.core.http.RequestTest;
import com.predic8.membrane.core.http.ResponseTest;
import com.predic8.membrane.core.interceptor.DispatchingInterceptorTest;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptorTest;
import com.predic8.membrane.core.interceptor.IndexInterceptorTest;
import com.predic8.membrane.core.interceptor.ThrottleInterceptorTest;
import com.predic8.membrane.core.interceptor.balancer.ClusterNotificationInterceptorTest;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingWithClusterManagerAndNoSessionTest;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingWithClusterManagerTest;
import com.predic8.membrane.core.interceptor.formvalidation.FormValidationInterceptorTest;
import com.predic8.membrane.core.interceptor.rest.HTTP2XMLInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptorTest;
import com.predic8.membrane.core.magic.MagicTest;
import com.predic8.membrane.core.rules.ServiceProxyKeyTest;
import com.predic8.membrane.core.transport.ExchangeTest;
import com.predic8.membrane.core.transport.http.HostColonPortTest;
import com.predic8.membrane.core.transport.http.HttpKeepAliveTest;
import com.predic8.membrane.core.transport.http.ServiceInvocationTest;
import com.predic8.membrane.core.transport.ssl.SSLContextTest;
import com.predic8.membrane.core.util.ByteUtilTest;
import com.predic8.membrane.core.util.ContentTypeDetectorTest;
import com.predic8.membrane.core.util.DNSCacheTest;
import com.predic8.membrane.core.util.HttpUtilTest;
import com.predic8.membrane.core.util.TextUtilTest;
import com.predic8.membrane.core.util.URITest;
import com.predic8.membrane.interceptor.MultipleLoadBalancersTest;

@RunWith(Suite.class)
@SuiteClasses({ HeaderTest.class, BodyTest.class, ByteUtilTest.class,
	HttpUtilTest.class, RequestTest.class, ResponseTest.class,
	MagicTest.class, 
	DispatchingInterceptorTest.class,
	HostColonPortTest.class,
	HTTP2XMLInterceptorTest.class, 
	RuleManagerTest.class, ProxyTest.class, ServiceProxyKeyTest.class,
  TextUtilTest.class, 
	ClusterNotificationInterceptorTest.class,
	LoadBalancingWithClusterManagerAndNoSessionTest.class,
	LoadBalancingWithClusterManagerTest.class,
	MultipleLoadBalancersTest.class, DNSCacheTest.class,
	ThrottleInterceptorTest.class, 
	FormValidationInterceptorTest.class, ServiceInvocationTest.class,
	HttpKeepAliveTest.class, ReverseProxyingInterceptorTest.class,
  HeaderFilterInterceptorTest.class,
	ContentTypeDetectorTest.class,
	LimitedMemoryExchangeStoreTest.class,
	IndexInterceptorTest.class,
	URITest.class,
	RewriteInterceptorTest.class,
	AbortExchangeTest.class
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
