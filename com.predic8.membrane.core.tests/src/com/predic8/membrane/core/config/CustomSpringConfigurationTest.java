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
package com.predic8.membrane.core.config;

import java.util.List;

import static junit.framework.Assert.*;

import org.eclipse.core.runtime.internal.stats.StatsManager;
import org.junit.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptor;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPMessageValidatorInterceptor;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.interceptor.statistics.StatisticsJDBCInterceptor;
import com.predic8.membrane.core.interceptor.statistics.StatisticsCSVInterceptor;

public class CustomSpringConfigurationTest {

	private Router router;
	
	@Before
	public void setUp() throws Exception {
		router = Router.init("resources/custom-spring-beans.xml");
	}

	@Test
	public void testInit() throws Exception {
	 	assertNotNull(router);
	 	assertNull(router.getClusterManager());
	 	assertTrue(router.getExchangeStore().getClass().getName().endsWith("MemoryExchangeStore"));
	 	assertTrue(router.getConfigurationManager().getConfiguration().getIndentMessage());
	 	assertTrue(router.getConfigurationManager().getConfiguration().getAdjustContentLength());
	 	assertTrue(router.getConfigurationManager().getConfiguration().getAdjustHostHeader());
	 	assertFalse(router.getConfigurationManager().getConfiguration().getTrackExchange());
	 	assertTrue(router==router.getConfigurationManager().getRouter());
	 	assertTrue(router==router.getConfigurationManager().getConfiguration().getRouter());
	 	assertTrue(router==router.getConfigurationManager().getConfigurationStore().getRouter());
	 	
	 	List<Interceptor> inters = router.getTransport().getInterceptors();
	 	System.out.println(inters.size());
	 	
	 	assertEquals("Dispatching Interceptor", inters.get(0).getDisplayName());

	 	assertForgetfulExchangeStore(((ExchangeStoreInterceptor)inters.get(1)));
	 	assertMemoryExchangeStore(((ExchangeStoreInterceptor)inters.get(2)));
	 	assertFileExchangeStore(((ExchangeStoreInterceptor)inters.get(3)));
	 	
	 	assertXsltInterceptor(((XSLTInterceptor)inters.get(4)));
	 	
	 	assertRegExUrlRewriterInterceptor((RegExURLRewriteInterceptor)inters.get(5));
	 	
	 	assertEquals("Administration", inters.get(6).getDisplayName());
	 	
	 	assertWebServerInterceptor((WebServerInterceptor)inters.get(7));
	 	
	 	assertLoadBalancingInterceptor((LoadBalancingInterceptor)inters.get(8));

	 	assertClusterNotificationInterceptor((ClusterNotificationInterceptor)inters.get(9));
	 	
	 	assertWSDLInterceptor((WSDLInterceptor)inters.get(10));

	 	assertStatisticsCSVInterceptor((StatisticsCSVInterceptor)inters.get(11));

	 	assertREST2SOAPInterceptor((REST2SOAPInterceptor)inters.get(12));

	 	assertSOAPMessageValidatorInterceptor((SOAPMessageValidatorInterceptor)inters.get(13));

	 	assertXPathCBRInterceptor((XPathCBRInterceptor)inters.get(14));
	 	
	 	assertXPathCBRInterceptor((RegExReplaceInterceptor)inters.get(15));

	 	assertCountInterceptor((CountInterceptor)inters.get(16));

	 	assertAccessControlInterceptor((AccessControlInterceptor)inters.get(17));
	 	
	 	//assertStatisticsJDBCInterceptor((StatisticsJDBCInterceptor)inters.get(13));

	 	List<Interceptor> backbones = router.getTransport().getBackboneInterceptors();
	 	
	 	assertEquals("Rule Matching Interceptor", backbones.get(0).getDisplayName());
	 	assertBasicAuthenticationInterceptor((BasicAuthenticationInterceptor) backbones.get(1));
	}

	private void assertCountInterceptor(CountInterceptor i) {
	 	assertEquals("Node 1", i.getDisplayName());
	}

	private void assertXPathCBRInterceptor(RegExReplaceInterceptor i) {
	 	assertEquals("Hallo", i.getPattern());
	 	assertEquals("Hello", i.getReplacement());
	}

	private void assertXPathCBRInterceptor(XPathCBRInterceptor i) {
	 	assertEquals("//convert", i.getRouteProvider().getRoutes().get(0).getxPath());
	 	assertEquals("http://www.thomas-bayer.com/axis2/", i.getRouteProvider().getRoutes().get(0).getUrl());
	}

	private void assertSOAPMessageValidatorInterceptor(SOAPMessageValidatorInterceptor i) {
	 	assertEquals("http://www.predic8.com:8080/material/ArticleService?wsdl", i.getWsdl());
	}

	private void assertREST2SOAPInterceptor(REST2SOAPInterceptor i) {
	 	assertEquals("/bank/.*", i.getMappings().get(0).regex);
	 	assertEquals("", i.getMappings().get(0).soapAction);
	 	assertEquals("/axis2/services/BLZService", i.getMappings().get(0).soapURI);
	 	assertEquals("request.xsl", i.getMappings().get(0).requestXSLT);
	 	assertEquals("response.xsl", i.getMappings().get(0).responseXSLT);
	}

	private void assertWSDLInterceptor(WSDLInterceptor i) {
	 	assertEquals("localhost", i.getHost());
	 	assertEquals("4000", i.getPort());
	 	assertEquals("http", i.getProtocol());
	 	assertEquals("http://predic8.de/register", i.getRegistryWSDLRegisterURL());
	}

	private void assertStatisticsJDBCInterceptor(StatisticsJDBCInterceptor i) {
	 	assertTrue(i.isPostMethodOnly());
	 	assertFalse(i.isSoapOnly());
	 	assertTrue(i.getDataSource().getClass().getName().endsWith("BasicDataSource"));
	}

	private void assertStatisticsCSVInterceptor(StatisticsCSVInterceptor i) {
	 	assertEquals("stat.csv", i.getFileName());
	}

	private void assertLoadBalancingInterceptor(LoadBalancingInterceptor i) {
	 	assertEquals("http://chat.predic8.com/", i.getSessionIdExtractor().getNamespace());
	 	assertEquals("session", i.getSessionIdExtractor().getLocalName());

	 	assertEquals("localhost", i.getEndpoints().get(0).getHost());
	 	assertEquals(8080, i.getEndpoints().get(0).getPort());
	 	
	 	assertEquals(10, ((ByThreadStrategy)i.getDispatchingStrategy()).getMaxNumberOfThreadsPerEndpoint());
	 	assertEquals(1000, ((ByThreadStrategy)i.getDispatchingStrategy()).getRetryTimeOnBusy());
	}

	private void assertClusterNotificationInterceptor(ClusterNotificationInterceptor i) {
	 	assertEquals(true, i.isValidateSignature());
	 	assertEquals("2324920293", i.getKeyHex());
	 	assertEquals(5000, i.getTimeout());
	}

	private void assertAccessControlInterceptor(AccessControlInterceptor i) {
	 	assertEquals("resources/acl/acl.xml", i.getAclFilename());
	}

	private void assertWebServerInterceptor(WebServerInterceptor i) {
	 	assertEquals("docBase", i.getDocBase());
	}

	private void assertBasicAuthenticationInterceptor(BasicAuthenticationInterceptor i) {
	 	assertTrue(i.getUsers().containsKey("jim"));
	 	assertTrue(i.getUsers().containsValue("password"));
	}

	private void assertRegExUrlRewriterInterceptor(RegExURLRewriteInterceptor i) {
	 	assertEquals("/bank/", i.getMappings().get(0).regex);
	 	assertEquals("/axis2/", i.getMappings().get(0).uri);
	}

	private void assertXsltInterceptor(XSLTInterceptor i) {
	 	assertEquals("/test/request.xslt", i.getRequestXSLT());
	 	assertEquals("/test/response.xslt", i.getResponseXSLT());
	}

	private void assertForgetfulExchangeStore(ExchangeStoreInterceptor i) {
		assertNotNull((ForgetfulExchangeStore)i.getExchangeStore());
	}

	private void assertMemoryExchangeStore(ExchangeStoreInterceptor i) {
		assertNotNull((MemoryExchangeStore)i.getExchangeStore());
	}

	private void assertFileExchangeStore(ExchangeStoreInterceptor i) {
		assertFalse(((FileExchangeStore)i.getExchangeStore()).isRaw());
		assertFalse(((FileExchangeStore)i.getExchangeStore()).isSaveBodyOnly());
		assertEquals("temp", ((FileExchangeStore)i.getExchangeStore()).getDir());
	}
		
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

}
