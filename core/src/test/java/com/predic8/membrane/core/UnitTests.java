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

import com.predic8.membrane.core.cli.CliCommandTest;
import com.predic8.membrane.core.config.CustomSpringConfigurationTest;
import com.predic8.membrane.core.config.ProxyTest;
import com.predic8.membrane.core.config.ReadRulesConfigurationTest;
import com.predic8.membrane.core.config.ReadRulesWithInterceptorsConfigurationTest;
import com.predic8.membrane.core.exceptions.ProblemDetailsTest;
import com.predic8.membrane.core.exchangestore.AbortExchangeTest;
import com.predic8.membrane.core.exchangestore.AbstractExchangeStoreTest;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStoreTest;
import com.predic8.membrane.core.graphql.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.cookie.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.interceptor.acl.matchers.Cidr.*;
import com.predic8.membrane.core.interceptor.apikey.*;
import com.predic8.membrane.core.interceptor.apikey.extractors.*;
import com.predic8.membrane.core.interceptor.apikey.stores.*;
import com.predic8.membrane.core.interceptor.authentication.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.beautifier.*;
import com.predic8.membrane.core.interceptor.cbr.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.formvalidation.*;
import com.predic8.membrane.core.interceptor.groovy.*;
import com.predic8.membrane.core.interceptor.javascript.*;
import com.predic8.membrane.core.interceptor.json.*;
import com.predic8.membrane.core.interceptor.log.*;
import com.predic8.membrane.core.interceptor.misc.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.ratelimit.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.security.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.interceptor.xml.*;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.*;
import com.predic8.membrane.core.interceptor.xmlprotection.*;
import com.predic8.membrane.core.interceptor.xslt.*;
import com.predic8.membrane.core.kubernetes.client.*;
import com.predic8.membrane.core.lang.spel.*;
import com.predic8.membrane.core.lang.spel.functions.*;
import com.predic8.membrane.core.magic.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http2.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.transport.ssl.acme.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.ws.*;
import com.predic8.membrane.core.ws.relocator.*;
import com.predic8.membrane.interceptor.*;
import org.junit.platform.suite.api.*;

@Suite
@SelectClasses({HeaderTest.class, BodyTest.class, ByteUtilTest.class,
		XMLUtilTest.class,
        HttpUtilTest.class,
		RequestTest.class,
		RequestBuilderTest.class,
		ResponseTest.class,
		ResponseBuilderTest.class,
		BasicAuthenticationInterceptorTest.class,
		StaticUserDataProviderTest.class,
        MagicTest.class,
		WSDLUtilTest.class,
		WSDLValidatorTest.class,
		WSDLInterceptorTest.class,
        AccessControlParserTest.class, HostnameTest.class, ParseTypeTest.class, IpRangeTest.class,
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
        SOAPUtilTest.class, SoapOperationExtractorTest.class,
		SoapProxyInvocationTest.class, // @TODO Check Proper naming!
		SOAPProxyTest.class,
        ContentTypeDetectorTest.class,
        MessageAnalyserTest.class, ExchangeTest.class,
        LimitedMemoryExchangeStoreTest.class,
        IndexInterceptorTest.class,
        SingleResolverTest.class,
		ResolverMapTest.class,
        JSONSchemaValidationTest.class,
        SOAPMessageValidatorInterceptorTest.class,
        URITest.class,
        RewriteInterceptorTest.class,
        AbortExchangeTest.class, RateLimitInterceptorTest.class,
        OAuth2UnitTests.class, SessionResumptionTest.class,
		Xml2JsonInterceptorTest.class, Json2XmlInterceptorTest.class, TemplateInterceptorTest.class,
		XmlPathExtractorInterceptorTest.class, JsonPointerExtractorInterceptorTest.class,
		AcmeStepTest.class, AcmeRenewTest.class, KubernetesClientTest.class,
		ProxyTest.class, Http2ClientServerTest.class, ChunkedBodyTest.class,
		ReturnInterceptorTest.class,
		JavascriptInterceptor.class,
		MimeTypeTest.class,
		MessageBytesTest.class,
		RegExReplaceInterceptorTest.class,
		URLParamUtilTest.class,
		XMLProtectorTest.class,
		AbstractExchangeStoreTest.class,
		JsonProtectionInterceptorTest.class,
		GraphQLProtectionInterceptorTest.class,
		GraphQLoverHttpValidatorTest.class,
		BeautifierInterceptorTest.class,
		ExchangeEvaluationContextTest.class,
		PaddingHeaderInterceptorTest.class,
		CollectionsUtilTest.class,
		ConditionalInterceptorGroovyTest.class,
		ConditionalInterceptorSpELTest.class,
		ReplaceInterceptorTest.class,
		ApiKeysInterceptorTest.class,
		ApiKeyFileStoreTest.class,
		ApiKeyHeaderExtractorTest.class,
		ApiKeyUtils.class,
		ReflectiveMethodHandlerTest.class,
		BuiltInFunctionsTest.class,
		AccessLogInterceptorTest.class,
		MediaTypeUtilTest.class,
		ProblemDetailsTest.class,
		SetHeaderInterceptor.class,
		SetPropertyInterceptor.class,
		APIProxyKeyTest.class,
		AdjustContentLengthTest.class,
		URIUtilTest.class,
		CliCommandTest.class,
		EchoInterceptorTest.class,
		XPathExpressionTest.class
})
@SelectPackages({"com.predic8.membrane.core.openapi",
				 "com.predic8.membrane.core.internalservice",
				 "com.predic8.membrane.core.interceptor.flow.invocation",
				 "com.predic8.membrane.core.acl"})
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