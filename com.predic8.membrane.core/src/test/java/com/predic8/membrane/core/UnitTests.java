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
package com.predic8.membrane.core;

import org.junit.runner.RunWith;
import org.junit.runners.*;
import org.junit.runners.Suite.SuiteClasses;

import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptorTest;
import com.predic8.membrane.core.interceptor.formvalidation.*;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptorTest;
import com.predic8.membrane.core.interceptor.rest.HTTP2XMLInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.SimpleURLRewriteInterceptorTest;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptorTest;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptorTest;
import com.predic8.membrane.core.magic.MagicTest;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.ws.relocator.RelocatorTest;
import com.predic8.membrane.interceptor.MultipleLoadBalancersTest;

@RunWith(Suite.class)
@SuiteClasses( { 
	HeaderTest.class,
	BodyTest.class,
	ByteUtilTest.class,
	HttpUtilTest.class,
	RequestTest.class,
	ResponseTest.class,
	MagicTest.class,
	CoreActivatorTest.class,	
	WSDLInterceptorTest.class,
	AccessControlParserTest.class,
	AccessControlInterceptorTest.class,
	DispatchingInterceptorTest.class,
	SimpleURLRewriteInterceptorTest.class,
	HostColonPortTest.class,
	HTTP2XMLInterceptorTest.class,	
	ReadRulesConfigurationTest.class,
	ReadRulesWithInterceptorsConfigurationTest.class,
	RuleManagerTest.class,
	ProxyTest.class,
	ServiceProxyKeyTest.class,
	ProxyRuleTest.class,
	TextUtilTest.class,
	RelocatorTest.class,
	XSLTInterceptorTest.class,
	URLUtilTest.class,
	ClusterManagerTest.class,
	ClusterNotificationInterceptorTest.class,	
	XMLSessionIdExtractorTest.class,
	ClusterBalancerTest.class,
	LoadBalancingWithClusterManagerAndNoSessionTest.class,
	LoadBalancingWithClusterManagerTest.class,
	MultipleLoadBalancersTest.class,
	DNSCacheTest.class,
	ValidatorInterceptorTest.class,
	XPathCBRInterceptorTest.class,
	CustomSpringConfigurationTest.class,	
	JSESSIONIDExtractorTest.class,
	ThrottleInterceptorTest.class,
	GroovyInterceptorTest.class,
	FormValidationInterceptorTest.class,
	ServiceInvocationTest.class,
	HttpKeepAliveTest.class,
	ReverseProxyingInterceptorTest.class,
})
public class UnitTests {

}
