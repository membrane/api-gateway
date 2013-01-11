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

import static junit.framework.Assert.*;

import java.io.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.xpath.*;

import org.junit.*;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.Router;

public class CustomRulesConfigurationTest {

	private Router router;
	XPath xpath = XPathFactory.newInstance().newXPath();

	@Before
	public void setUp() throws Exception {
		router = Router.init("src/test/resources/default-custom-beans.xml");
		router.getConfigurationManager().loadConfiguration(
				"src/test/resources/custom-rules.xml");
	}

	@Test
	public void testRulesConfig() throws Exception {
		StringWriter w = new StringWriter();
		router.getConfigurationManager().getProxies()
				.write(XMLOutputFactory.newInstance().createXMLStreamWriter(w));
		assertAttribute(w.toString(), "/proxies/serviceProxy/@name",
				"Service Proxy");
		assertAttribute(w.toString(), "/proxies/serviceProxy/@port", "2001");

		assertAttribute(w.toString(), "/proxies/serviceProxy/target/@port",
				"88");
		assertAttribute(w.toString(), "/proxies/serviceProxy/target/@host",
				"www.thomas-bayer.com");
		assertAttribute(w.toString(), "/proxies/serviceProxy/target/@url",
				"http://predic8.de/membrane.htm");

		assertAttribute(w.toString(), "/proxies/serviceProxy/transform/@xslt",
				"classpath:/strip-soap-envelope.xsl");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/counter/@name", "Node 1");

		/*
		 * assertElement(w.toString(),
		 * "/proxies/serviceProxy/request/adminConsole");
		 */

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/webServer/@docBase", "docBase");

		assertAttribute(
				w.toString(),
				"/proxies/serviceProxy/request/clusterNotification/@validateSignature",
				"true");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/clusterNotification/@keyHex",
				"6f488a642b740fb70c5250987a284dc0");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/clusterNotification/@timeout",
				"5000");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/basicAuthentication/user/@name",
				"admin");
		assertAttribute(
				w.toString(),
				"/proxies/serviceProxy/request/basicAuthentication/user/@password",
				"adminadmin");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/rewriter/map/@from", "^/home");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/rewriter/map/@to", "/index");

		assertElement(w.toString(),
				"/proxies/serviceProxy/request/xmlProtection");

		assertAttribute(w.toString(), "/proxies/serviceProxy/validator/@wsdl",
				"http://www.predic8.com:8080/material/ArticleService?wsdl");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/validator/@schema",
				"http://www.predic8.com:8080/material/ArticleService?xsd=2");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/rest2Soap/mapping/@regex", "/bank/.*");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/rest2Soap/mapping/@soapAction", "");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/rest2Soap/mapping/@soapURI",
				"/axis2/services/BLZService");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/rest2Soap/mapping/@requestXSLT",
				"request.xsl");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/rest2Soap/mapping/@responseXSLT",
				"response.xsl");

		assertAttribute(
				w.toString(),
				"/proxies/serviceProxy/balancer/xmlSessionIdExtractor/@namespace",
				"http://chat.predic8.com/");
		assertAttribute(
				w.toString(),
				"/proxies/serviceProxy/balancer/xmlSessionIdExtractor/@localName",
				"session");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/balancer/clusters/cluster/@name",
				"Default");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/balancer/clusters/cluster/node/@host",
				"localhost");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/balancer/clusters/cluster/node/@port",
				"3011");
		assertAttribute(
				w.toString(),
				"/proxies/serviceProxy/balancer/byThreadStrategy/@maxNumberOfThreadsPerEndpoint",
				"10");
		assertAttribute(
				w.toString(),
				"/proxies/serviceProxy/balancer/byThreadStrategy/@retryTimeOnBusy",
				"1000");

		assertElement(w.toString(),
				"/proxies/serviceProxy/balancer/jSessionIdExtractor");

		assertAttribute(w.toString(), "/proxies/serviceProxy/log/@headerOnly",
				"true");
		assertAttribute(w.toString(), "/proxies/serviceProxy/log/@category",
				"membrane");
		assertAttribute(w.toString(), "/proxies/serviceProxy/log/@level",
				"INFO");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/regExReplacer/@regex", "Hallo");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/regExReplacer/@replace",
				"Hello");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/switch/case/@xPath", "//convert");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/switch/case/@url",
				"http://www.thomas-bayer.com/axis2/");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/statisticsCSV/@file", "temp/stat.csv");

		assertAttribute(
				w.toString(),
				"/proxies/serviceProxy/response/wsdlRewriter/@registryWSDLRegisterURL",
				"http://predic8.de/register");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/wsdlRewriter/@protocol", "http");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/wsdlRewriter/@port", "4000");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/wsdlRewriter/@host",
				"localhost");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/wadlRewriter/@protocol",
				"https");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/wadlRewriter/@port", "443");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/response/wadlRewriter/@host", "abc.de");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/accessControl/@file",
				"src/test/resources/acl/acl.xml");

		assertAttribute(w.toString(), "/proxies/serviceProxy/groovy",
				"exc.setProperty('foo', 'bar');CONTINUE");

		assertAttribute(w.toString(), "/proxies/serviceProxy/throttle/@delay",
				"1000");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/throttle/@maxThreads", "5");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/throttle/@busyDelay", "3000");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/formValidation/field[1]/@name",
				"age");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/formValidation/field[1]/@regex",
				"\\d+");

		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/formValidation/field[2]/@name",
				"name");
		assertAttribute(w.toString(),
				"/proxies/serviceProxy/request/formValidation/field[2]/@regex",
				"[a-z]+");

		assertElement(w.toString(),
				"/proxies/serviceProxy/request/analyser");
		
		// assertAttribute(w.toString(),
		// "/proxies/serviceProxy/statisticsJDBC/@postMethodOnly", "false");
		// assertAttribute(w.toString(),
		// "/proxies/serviceProxy/statisticsJDBC/@soapOnly", "true");
		// assertAttribute(w.toString(),
		// "/proxies/serviceProxy/statisticsJDBC/dataSource/@driverClassName",
		// "com.mysql.jdbc.Driver");
		// assertAttribute(w.toString(),
		// "/proxies/serviceProxy/statisticsJDBC/dataSource/@url",
		// "jdbc:mysql://localhost/proxies");
		// assertAttribute(w.toString(),
		// "/proxies/serviceProxy/statisticsJDBC/dataSource/@user", "root");
		// assertAttribute(w.toString(),
		// "/proxies/serviceProxy/statisticsJDBC/dataSource/@password",
		// "rootroot");

		assertAttribute(w.toString(), "/proxies/proxy/@name", "HTTP Proxy");
		assertAttribute(w.toString(), "/proxies/proxy/@port", "3128");

		assertAttribute(w.toString(), "/proxies/proxy/transform/@xslt",
				"classpath:/strip-soap-envelope.xsl");

		assertAttribute(w.toString(), "/proxies/proxy/switch/case/@xPath",
				"//convert");
		assertAttribute(w.toString(), "/proxies/proxy/switch/case/@url",
				"http://www.thomas-bayer.com/axis2/");
	}

	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

	private void assertAttribute(String xml, String xpathExpr, String expected)
			throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(
				new StringReader(xml))));
	}

	private void assertElement(String xml, String xpathExpr)
			throws XPathExpressionException {
		assertNotNull(xpath.evaluate(xpathExpr, new InputSource(
				new StringReader(xml)), XPathConstants.NODE));
	}

}
