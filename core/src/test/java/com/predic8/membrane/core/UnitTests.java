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

import com.predic8.membrane.core.config.CustomSpringConfigurationTest;
import com.predic8.membrane.core.config.ProxyTest;
import com.predic8.membrane.core.config.ReadRulesConfigurationTest;
import com.predic8.membrane.core.config.ReadRulesWithInterceptorsConfigurationTest;
import com.predic8.membrane.core.http.BodyTest;
import com.predic8.membrane.core.http.HeaderTest;
import com.predic8.membrane.core.http.RequestTest;
import com.predic8.membrane.core.http.ResponseTest;
import com.predic8.membrane.core.interceptor.DispatchingInterceptorTest;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptorTest;
import com.predic8.membrane.core.interceptor.IndexInterceptorTest;
import com.predic8.membrane.core.interceptor.InternalInvocationTest;
import com.predic8.membrane.core.interceptor.MessageAnalyserTest;
import com.predic8.membrane.core.interceptor.ThrottleInterceptorTest;
import com.predic8.membrane.core.interceptor.WADLInterceptorTest;
import com.predic8.membrane.core.interceptor.WSDLInterceptorTest;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptorTest;
import com.predic8.membrane.core.interceptor.acl.AccessControlParserTest;
import com.predic8.membrane.core.interceptor.balancer.ClusterBalancerTest;
import com.predic8.membrane.core.interceptor.balancer.ClusterManagerTest;
import com.predic8.membrane.core.interceptor.balancer.ClusterNotificationInterceptorTest;
import com.predic8.membrane.core.interceptor.balancer.JSESSIONIDExtractorTest;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingWithClusterManagerAndNoSessionTest;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingWithClusterManagerTest;
import com.predic8.membrane.core.interceptor.balancer.XMLSessionIdExtractorTest;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptorTest;
import com.predic8.membrane.core.interceptor.formvalidation.FormValidationInterceptorTest;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptorTest;
import com.predic8.membrane.core.interceptor.rest.HTTP2XMLInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptorTest;
import com.predic8.membrane.core.interceptor.schemavalidation.JSONSchemaValidationTest;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPUtilTest;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptorTest;
import com.predic8.membrane.core.interceptor.soap.SoapOperationExtractorTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathAnalyzerTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParserTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.XMLContentFilterTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.XMLElementFinderTest;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptorTest;
import com.predic8.membrane.core.magic.MagicTest;
import com.predic8.membrane.core.multipart.ReassembleTest;
import com.predic8.membrane.core.resolver.SingleResolverTest;
import com.predic8.membrane.core.rules.ProxyRuleTest;
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
import com.predic8.membrane.core.util.URLUtilTest;
import com.predic8.membrane.core.ws.relocator.RelocatorTest;
import com.predic8.membrane.core.ws.relocator.RelocatorWADLTest;
import com.predic8.membrane.interceptor.MultipleLoadBalancersTest;

@RunWith(Suite.class)
@SuiteClasses({ HeaderTest.class, BodyTest.class, ByteUtilTest.class,
	HttpUtilTest.class, RequestTest.class, ResponseTest.class,
	MagicTest.class, WSDLInterceptorTest.class,
	AccessControlParserTest.class, AccessControlInterceptorTest.class,
	DispatchingInterceptorTest.class,
	HostColonPortTest.class,
	HTTP2XMLInterceptorTest.class, ReadRulesConfigurationTest.class,
	ReadRulesWithInterceptorsConfigurationTest.class,
	RuleManagerTest.class, ProxyTest.class, ServiceProxyKeyTest.class,
	ProxyRuleTest.class, TextUtilTest.class, RelocatorTest.class,
	XSLTInterceptorTest.class, URLUtilTest.class, ClusterManagerTest.class,
	ClusterNotificationInterceptorTest.class,
	XMLSessionIdExtractorTest.class, ClusterBalancerTest.class,
	LoadBalancingWithClusterManagerAndNoSessionTest.class,
	LoadBalancingWithClusterManagerTest.class,
	MultipleLoadBalancersTest.class, DNSCacheTest.class,
	ValidatorInterceptorTest.class, XPathCBRInterceptorTest.class,
	CustomSpringConfigurationTest.class, JSESSIONIDExtractorTest.class,
	ThrottleInterceptorTest.class, GroovyInterceptorTest.class,
	FormValidationInterceptorTest.class, ServiceInvocationTest.class,
	HttpKeepAliveTest.class, ReverseProxyingInterceptorTest.class,
	SSLContextTest.class, RelocatorWADLTest.class,
	WADLInterceptorTest.class, ReassembleTest.class,
	XMLContentFilterTest.class, XMLElementFinderTest.class,
	SimpleXPathAnalyzerTest.class, SimpleXPathParserTest.class,
	InternalInvocationTest.class, HeaderFilterInterceptorTest.class,
	SOAPUtilTest.class,	SoapOperationExtractorTest.class,
	ContentTypeDetectorTest.class,
	MessageAnalyserTest.class, ExchangeTest.class,
	LimitedMemoryExchangeStoreTest.class,
	IndexInterceptorTest.class,
	SingleResolverTest.class,
	JSONSchemaValidationTest.class
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
