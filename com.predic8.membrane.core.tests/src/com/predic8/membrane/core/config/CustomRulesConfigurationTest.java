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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.Router;

public class CustomRulesConfigurationTest {

	private Router router;
	XPath xpath = XPathFactory.newInstance().newXPath();
	
	@Before
	public void setUp() throws Exception {
		router = Router.init("resources/default-custom-beans.xml");
		router.getConfigurationManager().loadConfiguration("resources/custom-rules.xml");		
	}

	@Test
	public void testRulesConfig() throws Exception {
		StringWriter w = new StringWriter();
		router.getConfigurationManager().getConfiguration().write(XMLOutputFactory.newInstance().createXMLStreamWriter(w));
		
		assertAttribute(w.toString(), "//forwardingRule/@name", "Test Rule");
		assertAttribute(w.toString(), "//forwardingRule/@port", "2001");
		assertAttribute(w.toString(), "//forwardingRule/target/@port", "80");
		assertAttribute(w.toString(), "//forwardingRule/target/@host", "www.thomas-bayer.com");

		assertAttribute(w.toString(), "//interceptors/transform/@requestXSLT", "request.xslt");
		assertAttribute(w.toString(), "//interceptors/transform/@responseXSLT", "response.xslt");
		
		assertAttribute(w.toString(), "//interceptors/counter/@name", "Node 1");
		
		assertElement(w.toString(), "//interceptors/adminConsole");
		
		assertAttribute(w.toString(), "//interceptors/webServer/@docBase", "docBase");

		assertAttribute(w.toString(), "//interceptors/clusterNotification/@validateSignature", "true");
		assertAttribute(w.toString(), "//interceptors/clusterNotification/@keyHex", "6f488a642b740fb70c5250987a284dc0");
		assertAttribute(w.toString(), "//interceptors/clusterNotification/@timeout", "5000");

		assertAttribute(w.toString(), "//interceptors/basicAuthentication/user/@name", "admin");
		assertAttribute(w.toString(), "//interceptors/basicAuthentication/user/@password", "adminadmin");

		assertAttribute(w.toString(), "//interceptors/regExUrlRewriter/mapping/@regex", "/home");
		assertAttribute(w.toString(), "//interceptors/regExUrlRewriter/mapping/@uri", "/index");
		
		assertAttribute(w.toString(), "//interceptors/soapValidator/@wsdl", "http://www.predic8.com:8080/material/ArticleService?wsdl");

		assertAttribute(w.toString(), "//interceptors/rest2Soap/mapping/@regex", "/bank/.*");
		assertAttribute(w.toString(), "//interceptors/rest2Soap/mapping/@soapAction", "");
		assertAttribute(w.toString(), "//interceptors/rest2Soap/mapping/@soapURI", "/axis2/services/BLZService");
		assertAttribute(w.toString(), "//interceptors/rest2Soap/mapping/@requestXSLT", "request.xsl");
		assertAttribute(w.toString(), "//interceptors/rest2Soap/mapping/@responseXSLT", "response.xsl");
		
		assertAttribute(w.toString(), "//interceptors/balancer/xmlSessionIdExtractor/@namespace", "http://chat.predic8.com/");
		assertAttribute(w.toString(), "//interceptors/balancer/xmlSessionIdExtractor/@localName", "session");
		assertAttribute(w.toString(), "//interceptors/balancer/nodes/node/@host", "localhost");
		assertAttribute(w.toString(), "//interceptors/balancer/nodes/node/@port", "8080");
		assertAttribute(w.toString(), "//interceptors/balancer/byThreadStrategy/@maxNumberOfThreadsPerEndpoint", "10");
		assertAttribute(w.toString(), "//interceptors/balancer/byThreadStrategy/@retryTimeOnBusy", "1000");		

		assertAttribute(w.toString(), "//interceptors/interceptor/@id", "counter");		
		assertAttribute(w.toString(), "//interceptors/interceptor/@name", "Counter 2");		

		assertAttribute(w.toString(), "//interceptors/regExReplacer/@regex", "Hallo");		
		assertAttribute(w.toString(), "//interceptors/regExReplacer/@replace", "Hello");		

		assertAttribute(w.toString(), "//interceptors/cbr/route/@xPath", "//convert");		
		assertAttribute(w.toString(), "//interceptors/cbr/route/@url", "http://www.thomas-bayer.com/axis2/");		

		assertAttribute(w.toString(), "//interceptors/statisticsCSV/@file", "c:/temp/stat.csv");		

		assertAttribute(w.toString(), "//interceptors/wsdlRewriter/@registryWSDLRegisterURL", "http://predic8.de/register");		
		assertAttribute(w.toString(), "//interceptors/wsdlRewriter/@protocol", "http");		
		assertAttribute(w.toString(), "//interceptors/wsdlRewriter/@port", "4000");		
		assertAttribute(w.toString(), "//interceptors/wsdlRewriter/@host", "localhost");		

		assertAttribute(w.toString(), "//interceptors/accessControl/@file", "resources/acl/acl.xml");		

		assertAttribute(w.toString(), "//interceptors/exchangeStore/@name", "forgetfulExchangeStore");		
		
//		assertAttribute(w.toString(), "//interceptors/statisticsJDBC/@postMethodOnly", "false");		
//		assertAttribute(w.toString(), "//interceptors/statisticsJDBC/@soapOnly", "true");		
//		assertAttribute(w.toString(), "//interceptors/statisticsJDBC/dataSource/@driverClassName", "com.mysql.jdbc.Driver");		
//		assertAttribute(w.toString(), "//interceptors/statisticsJDBC/dataSource/@url", "jdbc:mysql://localhost/membrane");		
//		assertAttribute(w.toString(), "//interceptors/statisticsJDBC/dataSource/@user", "root");		
//		assertAttribute(w.toString(), "//interceptors/statisticsJDBC/dataSource/@password", "rootroot");		
	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

	private void assertAttribute(String xml, String xpathExpr, String expected) throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(new StringReader(xml))));
	}
	
	private void assertElement(String xml, String xpathExpr) throws XPathExpressionException {
		assertNotNull(xpath.evaluate(xpathExpr, new InputSource(new StringReader(xml)),XPathConstants.NODE));
	}


}
