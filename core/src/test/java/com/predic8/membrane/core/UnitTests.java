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

import com.predic8.membrane.core.config.CustomSpringConfigurationTest;
import com.predic8.membrane.core.config.ProxyTest;
import com.predic8.membrane.core.config.ReadRulesConfigurationTest;
import com.predic8.membrane.core.config.ReadRulesWithInterceptorsConfigurationTest;
import com.predic8.membrane.core.exceptions.ProblemDetailsTest;
import com.predic8.membrane.core.exchangestore.AbortExchangeTest;
import com.predic8.membrane.core.exchangestore.AbstractExchangeStoreTest;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStoreTest;
import com.predic8.membrane.core.graphql.GraphQLProtectionInterceptorTest;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.cookie.MessageBytesTest;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.AccessControlParserTest;
import com.predic8.membrane.core.interceptor.acl.HostnameTest;
import com.predic8.membrane.core.interceptor.acl.ParseTypeTest;
import com.predic8.membrane.core.interceptor.acl.matchers.Cidr.IpRangeTest;
import com.predic8.membrane.core.interceptor.apikey.ApiKeyUtils;
import com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptorTest;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyHeaderExtractorTest;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStoreTest;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptorTest;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProviderTest;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.beautifier.BeautifierInterceptorTest;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptorTest;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptorGroovyTest;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptorSpELTest;
import com.predic8.membrane.core.interceptor.formvalidation.FormValidationInterceptorTest;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptorTest;
import com.predic8.membrane.core.interceptor.javascript.JavascriptInterceptor;
import com.predic8.membrane.core.interceptor.json.JsonPointerExtractorInterceptorTest;
import com.predic8.membrane.core.interceptor.json.JsonProtectionInterceptorTest;
import com.predic8.membrane.core.interceptor.json.ReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.log.AccessLogInterceptorTest;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptorTest;
import com.predic8.membrane.core.interceptor.misc.SetHeaderInterceptor;
import com.predic8.membrane.core.interceptor.misc.SetPropertyInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2UnitTests;
import com.predic8.membrane.core.interceptor.ratelimit.RateLimitInterceptorTest;
import com.predic8.membrane.core.interceptor.rest.HTTP2XMLInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptorTest;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptorTest;
import com.predic8.membrane.core.interceptor.schemavalidation.JSONSchemaValidationTest;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPMessageValidatorInterceptorTest;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPUtilTest;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptorTest;
import com.predic8.membrane.core.interceptor.security.PaddingHeaderInterceptorTest;
import com.predic8.membrane.core.interceptor.soap.SoapOperationExtractorTest;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptorTest;
import com.predic8.membrane.core.interceptor.xml.Json2XmlInterceptorTest;
import com.predic8.membrane.core.interceptor.xml.Xml2JsonInterceptorTest;
import com.predic8.membrane.core.interceptor.xml.XmlPathExtractorInterceptorTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathAnalyzerTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParserTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.XMLContentFilterTest;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.XMLElementFinderTest;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectorTest;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptorTest;
import com.predic8.membrane.core.kubernetes.client.KubernetesClientTest;
import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContextTest;
import com.predic8.membrane.core.lang.spel.functions.BuiltInFunctionsTest;
import com.predic8.membrane.core.lang.spel.functions.ReflectiveMethodHandlerTest;
import com.predic8.membrane.core.magic.MagicTest;
import com.predic8.membrane.core.multipart.ReassembleTest;
import com.predic8.membrane.core.resolver.SingleResolverTest;
import com.predic8.membrane.core.rules.APIProxyKeyTest;
import com.predic8.membrane.core.rules.ProxyRuleTest;
import com.predic8.membrane.core.rules.ServiceProxyKeyTest;
import com.predic8.membrane.core.transport.ExchangeTest;
import com.predic8.membrane.core.transport.http.HostColonPortTest;
import com.predic8.membrane.core.transport.http.HttpKeepAliveTest;
import com.predic8.membrane.core.transport.http.ServiceInvocationTest;
import com.predic8.membrane.core.transport.http2.Http2ClientServerTest;
import com.predic8.membrane.core.transport.ssl.SSLContextTest;
import com.predic8.membrane.core.transport.ssl.SessionResumptionTest;
import com.predic8.membrane.core.transport.ssl.acme.AcmeRenewTest;
import com.predic8.membrane.core.transport.ssl.acme.AcmeStepTest;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.ws.relocator.RelocatorTest;
import com.predic8.membrane.core.ws.relocator.RelocatorWADLTest;
import com.predic8.membrane.interceptor.MultipleLoadBalancersTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({HeaderTest.class, BodyTest.class, ByteUtilTest.class,
        HttpUtilTest.class,
		RequestTest.class,
		RequestBuilderTest.class,
		ResponseTest.class,
		ResponseBuilderTest.class,
		BasicAuthenticationInterceptorTest.class,
		StaticUserDataProviderTest.class,
        MagicTest.class, WSDLInterceptorTest.class,
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
        ContentTypeDetectorTest.class,
        MessageAnalyserTest.class, ExchangeTest.class,
        LimitedMemoryExchangeStoreTest.class,
        IndexInterceptorTest.class,
        SingleResolverTest.class,
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
		AdjustContentLengthTest.class
})
@SelectPackages({"com.predic8.membrane.core.openapi",
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