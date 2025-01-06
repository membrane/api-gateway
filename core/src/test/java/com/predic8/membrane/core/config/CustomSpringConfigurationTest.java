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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.interceptor.authentication.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.server.*;
import com.predic8.membrane.core.interceptor.statistics.*;
import com.predic8.membrane.core.interceptor.xslt.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class CustomSpringConfigurationTest {

	private Router router;

	@SuppressWarnings("ResultOfMethodCallIgnored")
	@BeforeEach
	public void setUp() {
		new File("target/temp").mkdirs();
		router = Router.init("src/test/resources/custom-spring-beans.xml");
	}

	@Disabled
	@Test
	public void testInit() {
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
		// assertCountInterceptor((CountInterceptor)inters.get(16));
		//
		// assertAccessControlInterceptor((AccessControlInterceptor)inters.get(17));

		// assertStatisticsJDBCInterceptor((StatisticsJDBCInterceptor)inters.get(13));

		// assertBasicAuthenticationInterceptor((BasicAuthenticationInterceptor)
		// backbones.get(1));
		assertTrue(((HTTPClientInterceptor)inters.getLast()).isAdjustHostHeader());
	}

	private void assertCountInterceptor(CountInterceptor i) {
		assertEquals("Node 1", i.getDisplayName());
	}

	private void assertXPathCBRInterceptor(RegExReplaceInterceptor i) {
		assertEquals("Hallo", i.getRegex());
		assertEquals("Hello", i.getReplace());
	}

	private void assertSOAPMessageValidatorInterceptor(ValidatorInterceptor i) {
		assertEquals(
				"http://www.predic8.com:8080/material/ArticleService?wsdl",
				i.getWsdl());
	}

	private void assertREST2SOAPInterceptor(REST2SOAPInterceptor i) {
		assertEquals("/bank/.*", i.getMappings().getFirst().regex);
		assertEquals("", i.getMappings().getFirst().soapAction);
		assertEquals("/axis2/services/BLZService",
				i.getMappings().getFirst().soapURI);
		assertEquals("request.xsl", i.getMappings().getFirst().requestXSLT);
		assertEquals("response.xsl", i.getMappings().getFirst().responseXSLT);
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

		assertEquals("localhost", i.getEndpoints().getFirst().getHost());
		assertEquals(3011, i.getEndpoints().getFirst().getPort());

		assertEquals(10,
				((ByThreadStrategy) i.getDispatchingStrategy())
				.getMaxNumberOfThreadsPerEndpoint());
		assertEquals(1000,
				((ByThreadStrategy) i.getDispatchingStrategy())
				.getRetryTimeOnBusy());
	}

	private void assertClusterNotificationInterceptor(
			ClusterNotificationInterceptor i) {
		assertTrue(i.isValidateSignature());
		assertEquals("2324920293", i.getKeyHex());
		assertEquals(5000, i.getTimeout());
	}

	private void assertAccessControlInterceptor(AccessControlInterceptor i) {
		assertEquals("src/test/resources/acl/acl.xml", i.getFile());
	}

	private void assertWebServerInterceptor(WebServerInterceptor i) {
		assertEquals("docBase", i.getDocBase());
	}

	@SuppressWarnings("OptionalGetWithoutIsPresent")
	private void assertBasicAuthenticationInterceptor(
			BasicAuthenticationInterceptor i) {
		assertTrue(i.getUsers().stream().anyMatch(u -> u.getUsername().equals("jim")));
		assertEquals("password", i.getUsers().stream().filter(u -> u.getUsername().equals("jim")).findFirst().get().getPassword());
	}

	private void assertRewriterInterceptor(RewriteInterceptor i) {
		assertEquals("^/bank/", i.getMappings().getFirst().from);
		assertEquals("^/axis2/", i.getMappings().getFirst().to);
	}

	private void assertXsltInterceptor(XSLTInterceptor i) {
		assertEquals("/test/strip.xslt", i.getXslt());
	}

	private void assertForgetfulExchangeStore(ExchangeStoreInterceptor i) {
		assertNotNull(i.getExchangeStore());
	}

	private void assertMemoryExchangeStore(ExchangeStoreInterceptor i) {
		assertNotNull(i.getExchangeStore());
	}

	private void assertFileExchangeStore(ExchangeStoreInterceptor i) {
		assertFalse(((FileExchangeStore) i.getExchangeStore()).isRaw());
		assertFalse(((FileExchangeStore) i.getExchangeStore()).isSaveBodyOnly());
		assertEquals("temp",
				((FileExchangeStore) i.getExchangeStore()).getDir());
	}

	@AfterEach
	public void tearDown() {
		router.shutdown();
	}

}
