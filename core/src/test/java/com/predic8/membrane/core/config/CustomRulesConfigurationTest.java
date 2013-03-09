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
		router = Router.init("src/test/resources/custom-rules.xml");
	}

	@Test
	public void testRulesConfig() throws Exception {
		StringWriter w = new StringWriter();
		router.write(XMLOutputFactory.newInstance().createXMLStreamWriter(w));
		assertAttribute(w.toString(), "/router/serviceProxy/@name",
				"Service Proxy");
		assertAttribute(w.toString(), "/router/serviceProxy/@port", "2001");

		assertAttribute(w.toString(), "/router/serviceProxy/target/@port",
				"88");
		assertAttribute(w.toString(), "/router/serviceProxy/target/@host",
				"www.thomas-bayer.com");
		assertAttribute(w.toString(), "/router/serviceProxy/target/@url",
				"http://predic8.de/membrane.htm");

		assertAttribute(w.toString(), "/router/serviceProxy/transform/@xslt",
				"classpath:/strip-soap-envelope.xsl");

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/counter/@name", "Node 1");

		/*
		 * assertElement(w.toString(),
		 * "/router/serviceProxy/request/adminConsole");
		 */

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/webServer/@docBase", "docBase");

		assertAttribute(
				w.toString(),
				"/router/serviceProxy/request/clusterNotification/@validateSignature",
				"true");
		assertAttribute(w.toString(),
				"/router/serviceProxy/request/clusterNotification/@keyHex",
				"6f488a642b740fb70c5250987a284dc0");
		assertAttribute(w.toString(),
				"/router/serviceProxy/request/clusterNotification/@timeout",
				"5000");

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/basicAuthentication/user/@name",
				"admin");
		assertAttribute(
				w.toString(),
				"/router/serviceProxy/request/basicAuthentication/user/@password",
				"adminadmin");

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/rewriter/map/@from", "^/home");
		assertAttribute(w.toString(),
				"/router/serviceProxy/request/rewriter/map/@to", "/index");

		assertElement(w.toString(),
				"/router/serviceProxy/request/xmlProtection");

		assertAttribute(w.toString(), "/router/serviceProxy/validator/@wsdl",
				"http://www.predic8.com:8080/material/ArticleService?wsdl");
		assertAttribute(w.toString(),
				"/router/serviceProxy/validator/@schema",
				"http://www.predic8.com:8080/material/ArticleService?xsd=2");

		assertAttribute(w.toString(),
				"/router/serviceProxy/rest2Soap/mapping/@regex", "/bank/.*");
		assertAttribute(w.toString(),
				"/router/serviceProxy/rest2Soap/mapping/@soapAction", "");
		assertAttribute(w.toString(),
				"/router/serviceProxy/rest2Soap/mapping/@soapURI",
				"/axis2/services/BLZService");
		assertAttribute(w.toString(),
				"/router/serviceProxy/rest2Soap/mapping/@requestXSLT",
				"request.xsl");
		assertAttribute(w.toString(),
				"/router/serviceProxy/rest2Soap/mapping/@responseXSLT",
				"response.xsl");

		assertAttribute(
				w.toString(),
				"/router/serviceProxy/balancer/xmlSessionIdExtractor/@namespace",
				"http://chat.predic8.com/");
		assertAttribute(
				w.toString(),
				"/router/serviceProxy/balancer/xmlSessionIdExtractor/@localName",
				"session");
		assertAttribute(w.toString(),
				"/router/serviceProxy/balancer/clusters/cluster/@name",
				"Default");
		assertAttribute(w.toString(),
				"/router/serviceProxy/balancer/clusters/cluster/node/@host",
				"localhost");
		assertAttribute(w.toString(),
				"/router/serviceProxy/balancer/clusters/cluster/node/@port",
				"3011");
		assertAttribute(
				w.toString(),
				"/router/serviceProxy/balancer/byThreadStrategy/@maxNumberOfThreadsPerEndpoint",
				"10");
		assertAttribute(
				w.toString(),
				"/router/serviceProxy/balancer/byThreadStrategy/@retryTimeOnBusy",
				"1000");

		assertElement(w.toString(),
				"/router/serviceProxy/balancer/jSessionIdExtractor");

		assertAttribute(w.toString(), "/router/serviceProxy/log/@headerOnly",
				"true");
		assertAttribute(w.toString(), "/router/serviceProxy/log/@category",
				"membrane");
		assertAttribute(w.toString(), "/router/serviceProxy/log/@level",
				"INFO");

		assertAttribute(w.toString(),
				"/router/serviceProxy/response/regExReplacer/@regex", "Hallo");
		assertAttribute(w.toString(),
				"/router/serviceProxy/response/regExReplacer/@replace",
				"Hello");

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/switch/case/@xPath", "//convert");
		assertAttribute(w.toString(),
				"/router/serviceProxy/request/switch/case/@url",
				"http://www.thomas-bayer.com/axis2/");

		assertAttribute(w.toString(),
				"/router/serviceProxy/statisticsCSV/@file", "temp/stat.csv");

		assertAttribute(
				w.toString(),
				"/router/serviceProxy/response/wsdlRewriter/@registryWSDLRegisterURL",
				"http://predic8.de/register");
		assertAttribute(w.toString(),
				"/router/serviceProxy/response/wsdlRewriter/@protocol", "http");
		assertAttribute(w.toString(),
				"/router/serviceProxy/response/wsdlRewriter/@port", "4000");
		assertAttribute(w.toString(),
				"/router/serviceProxy/response/wsdlRewriter/@host",
				"localhost");

		assertAttribute(w.toString(),
				"/router/serviceProxy/response/wadlRewriter/@protocol",
				"https");
		assertAttribute(w.toString(),
				"/router/serviceProxy/response/wadlRewriter/@port", "443");
		assertAttribute(w.toString(),
				"/router/serviceProxy/response/wadlRewriter/@host", "abc.de");

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/accessControl/@file",
				"src/test/resources/acl/acl.xml");

		assertAttribute(w.toString(), "/router/serviceProxy/groovy",
				"exc.setProperty('foo', 'bar');CONTINUE");

		assertAttribute(w.toString(), "/router/serviceProxy/throttle/@delay",
				"1000");
		assertAttribute(w.toString(),
				"/router/serviceProxy/throttle/@maxThreads", "5");
		assertAttribute(w.toString(),
				"/router/serviceProxy/throttle/@busyDelay", "3000");

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/formValidation/field[1]/@name",
				"age");
		assertAttribute(w.toString(),
				"/router/serviceProxy/request/formValidation/field[1]/@regex",
				"\\d+");

		assertAttribute(w.toString(),
				"/router/serviceProxy/request/formValidation/field[2]/@name",
				"name");
		assertAttribute(w.toString(),
				"/router/serviceProxy/request/formValidation/field[2]/@regex",
				"[a-z]+");

		assertElement(w.toString(),
				"/router/serviceProxy/request/analyser");
		
		// assertAttribute(w.toString(),
		// "/router/serviceProxy/statisticsJDBC/@postMethodOnly", "false");
		// assertAttribute(w.toString(),
		// "/router/serviceProxy/statisticsJDBC/@soapOnly", "true");
		// assertAttribute(w.toString(),
		// "/router/serviceProxy/statisticsJDBC/dataSource/@driverClassName",
		// "com.mysql.jdbc.Driver");
		// assertAttribute(w.toString(),
		// "/router/serviceProxy/statisticsJDBC/dataSource/@url",
		// "jdbc:mysql://localhost/proxies");
		// assertAttribute(w.toString(),
		// "/router/serviceProxy/statisticsJDBC/dataSource/@user", "root");
		// assertAttribute(w.toString(),
		// "/router/serviceProxy/statisticsJDBC/dataSource/@password",
		// "rootroot");

		assertAttribute(w.toString(), "/router/proxy/@name", "HTTP Proxy");
		assertAttribute(w.toString(), "/router/proxy/@port", "3128");

		assertAttribute(w.toString(), "/router/proxy/transform/@xslt",
				"classpath:/strip-soap-envelope.xsl");

		assertAttribute(w.toString(), "/router/proxy/switch/case/@xPath",
				"//convert");
		assertAttribute(w.toString(), "/router/proxy/switch/case/@url",
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
