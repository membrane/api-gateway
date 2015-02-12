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
package com.predic8.membrane.core.config;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptor;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.interceptor.statistics.*;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;

@SuppressWarnings("unused")
public class CustomSpringConfigurationTest {

	private Router router;

	@Before
	public void setUp() throws Exception {
		new File("target/temp").mkdirs();
		router = Router.init("src/test/resources/custom-spring-beans.xml");
	}

	@Test
	public void testInit() throws Exception {
		assertNotNull(router);
		assertTrue(router.getExchangeStore().getClass().getName()
				.endsWith("MemoryExchangeStore"));

		List<Interceptor> inters = router.getTransport().getInterceptors();

		assertEquals("Rule Matching Interceptor", inters.get(0)
				.getDisplayName());
		assertEquals("Dispatching Interceptor", inters.get(1).getDisplayName());

		// TODO monitor-beans test
		// assertForgetfulExchangeStore(((ExchangeStoreInterceptor)inters.get(1)));
		// assertMemoryExchangeStore(((ExchangeStoreInterceptor)inters.get(2)));
		// assertFileExchangeStore(((ExchangeStoreInterceptor)inters.get(3)));
		//
		// assertXsltInterceptor(((XSLTInterceptor)inters.get(4)));
		//
		// assertRegExUrlRewriterInterceptor((RegExURLRewriteInterceptor)inters.get(5));
		//
		// assertEquals("Administration", inters.get(6).getDisplayName());
		//
		// assertWebServerInterceptor((WebServerInterceptor)inters.get(7));
		//
		// assertLoadBalancingInterceptor((LoadBalancingInterceptor)inters.get(8));
		//
		// assertClusterNotificationInterceptor((ClusterNotificationInterceptor)inters.get(9));
		//
		// assertWSDLInterceptor((WSDLInterceptor)inters.get(10));
		//
		// assertStatisticsCSVInterceptor((StatisticsCSVInterceptor)inters.get(11));
		//
		// assertREST2SOAPInterceptor((REST2SOAPInterceptor)inters.get(12));
		//
		// assertSOAPMessageValidatorInterceptor((SoapValidatorInterceptor)inters.get(13));
		//
		// assertXPathCBRInterceptor((XPathCBRInterceptor)inters.get(14));
		//
		// assertXPathCBRInterceptor((RegExReplaceInterceptor)inters.get(15));
		//
		// assertCountInterceptor((CountInterceptor)inters.get(16));
		//
		// assertAccessControlInterceptor((AccessControlInterceptor)inters.get(17));

		// assertStatisticsJDBCInterceptor((StatisticsJDBCInterceptor)inters.get(13));

		// assertBasicAuthenticationInterceptor((BasicAuthenticationInterceptor)
		// backbones.get(1));
		assertTrue(((HTTPClientInterceptor)inters.get(inters.size()-1)).isAdjustHostHeader());
	}

	private void assertCountInterceptor(CountInterceptor i) {
		assertEquals("Node 1", i.getDisplayName());
	}

	private void assertXPathCBRInterceptor(RegExReplaceInterceptor i) {
		assertEquals("Hallo", i.getRegex());
		assertEquals("Hello", i.getReplace());
	}

	private void assertXPathCBRInterceptor(XPathCBRInterceptor i) {
		assertEquals("//convert", i.getCases().get(0)
				.getxPath());
		assertEquals("http://www.thomas-bayer.com/axis2/", i.getCases().get(0).getUrl());
	}

	private void assertSOAPMessageValidatorInterceptor(ValidatorInterceptor i) {
		assertEquals(
				"http://www.predic8.com:8080/material/ArticleService?wsdl",
				i.getWsdl());
	}

	private void assertREST2SOAPInterceptor(REST2SOAPInterceptor i) {
		assertEquals("/bank/.*", i.getMappings().get(0).regex);
		assertEquals("", i.getMappings().get(0).soapAction);
		assertEquals("/axis2/services/BLZService",
				i.getMappings().get(0).soapURI);
		assertEquals("request.xsl", i.getMappings().get(0).requestXSLT);
		assertEquals("response.xsl", i.getMappings().get(0).responseXSLT);
	}

	private void assertWSDLInterceptor(WSDLInterceptor i) {
		assertEquals("localhost", i.getHost());
		assertEquals("4000", i.getPort());
		assertEquals("http", i.getProtocol());
		assertEquals("http://predic8.de/register",
				i.getRegistryWSDLRegisterURL());
	}

	private void assertStatisticsJDBCInterceptor(StatisticsJDBCInterceptor i) {
		assertTrue(i.isPostMethodOnly());
		assertFalse(i.isSoapOnly());
		assertTrue(i.getDataSource().getClass().getName()
				.endsWith("BasicDataSource"));
	}

	private void assertStatisticsCSVInterceptor(StatisticsCSVInterceptor i) {
		assertEquals("stat.csv", i.getFileName());
	}

	private void assertLoadBalancingInterceptor(LoadBalancingInterceptor i) {
		XMLElementSessionIdExtractor ext = (XMLElementSessionIdExtractor) i
				.getSessionIdExtractor();
		assertEquals("http://chat.predic8.com/", ext.getNamespace());
		assertEquals("session", ext.getLocalName());

		assertEquals("localhost", i.getEndpoints().get(0).getHost());
		assertEquals(3011, i.getEndpoints().get(0).getPort());

		assertEquals(10,
				((ByThreadStrategy) i.getDispatchingStrategy())
						.getMaxNumberOfThreadsPerEndpoint());
		assertEquals(1000,
				((ByThreadStrategy) i.getDispatchingStrategy())
						.getRetryTimeOnBusy());
	}

	private void assertClusterNotificationInterceptor(
			ClusterNotificationInterceptor i) {
		assertEquals(true, i.isValidateSignature());
		assertEquals("2324920293", i.getKeyHex());
		assertEquals(5000, i.getTimeout());
	}

	private void assertAccessControlInterceptor(AccessControlInterceptor i) {
		assertEquals("src/test/resources/acl/acl.xml", i.getFile());
	}

	private void assertWebServerInterceptor(WebServerInterceptor i) {
		assertEquals("docBase", i.getDocBase());
	}

	private void assertBasicAuthenticationInterceptor(
			BasicAuthenticationInterceptor i) {
		assertTrue(i.getUsersByName().containsKey("jim"));
		assertEquals("password", i.getUsersByName().get("jim").getPassword());
	}

	private void assertRewriterInterceptor(RewriteInterceptor i) {
		assertEquals("^/bank/", i.getMappings().get(0).from);
		assertEquals("^/axis2/", i.getMappings().get(0).to);
	}

	private void assertXsltInterceptor(XSLTInterceptor i) {
		assertEquals("/test/strip.xslt", i.getXslt());
	}

	private void assertForgetfulExchangeStore(ExchangeStoreInterceptor i) {
		assertNotNull((ForgetfulExchangeStore) i.getExchangeStore());
	}

	private void assertMemoryExchangeStore(ExchangeStoreInterceptor i) {
		assertNotNull((MemoryExchangeStore) i.getExchangeStore());
	}

	private void assertFileExchangeStore(ExchangeStoreInterceptor i) {
		assertFalse(((FileExchangeStore) i.getExchangeStore()).isRaw());
		assertFalse(((FileExchangeStore) i.getExchangeStore()).isSaveBodyOnly());
		assertEquals("temp",
				((FileExchangeStore) i.getExchangeStore()).getDir());
	}

	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

}
