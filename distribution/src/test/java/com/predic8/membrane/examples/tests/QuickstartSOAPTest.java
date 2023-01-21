/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.assertContainsNot;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.*;

import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.examples.util.ProxiesXmlUtil;
import com.predic8.membrane.test.AssertUtils;

// TODO Remove when test for new Quickstart is written
public class QuickstartSOAPTest extends DistributionExtractingTestcase {

	@Test
	public void doit() throws IOException, InterruptedException {
		File baseDir = getExampleDir("quickstart-soap");
		Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
		try {
			ProxiesXmlUtil pxu = new ProxiesXmlUtil(new File(baseDir, "proxies.xml"));
			pxu.updateWith(
					"<spring:beans xmlns=\"http://membrane-soa.org/proxies/1/\"\r\n" +
							"	xmlns:spring=\"http://www.springframework.org/schema/beans\"\r\n" +
							"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
							"	xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r\n" +
							"					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd\">\r\n" +
							"\r\n" +
							"	<router>\r\n" +
							"	\r\n" +
							"	<soapProxy port=\"2000\" wsdl=\"http://www.thomas-bayer.com/axis2/services/BLZService?wsdl\">\r\n" +
							"		<path>/MyBLZService</path>\r\n" +
							"	</soapProxy>\r\n" +
							"	\r\n" +
							"	<serviceProxy port=\"9000\">\r\n" +
							"		<basicAuthentication>\r\n" +
							"			<user name=\"admin\" password=\"membrane\" />\r\n" +
							"		</basicAuthentication>	\r\n" +
							"		<adminConsole />\r\n" +
							"	</serviceProxy>\r\n" +
							"	\r\n" +
							"	</router>\r\n" +
							"</spring:beans>", sl);

			String endpoint = "http://localhost:2000/MyBLZService";
			String result = getAndAssert200(endpoint + "?wsdl");
			assertContains("wsdl:documentation", result);
			assertContains("localhost:2000/MyBLZService", result);  // assert that rewriting did take place

			result = AssertUtils.postAndAssert200(endpoint,
					"<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:blz=\"http://thomas-bayer.com/blz/\">\r\n" +
							"   <soapenv:Header/>\r\n" +
							"   <soapenv:Body>\r\n" +
							"      <blz:getBank>\r\n" +
							"         <blz:blz>37050198</blz:blz>\r\n" +
							"      </blz:getBank>\r\n" +
							"   </soapenv:Body>\r\n" +
					"</soapenv:Envelope>");
			assertContains("Sparkasse", result);

			AssertUtils.setupHTTPAuthentication("localhost", 9000, "admin", "membrane");
			result = getAndAssert200("http://localhost:9000/admin/");
			assertTrue(result.contains("BLZService"));

			String invalidRequest =
					"<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:blz=\"http://thomas-bayer.com/blz/\">\r\n" +
							"   <soapenv:Header/>\r\n" +
							"   <soapenv:Body>\r\n" +
							"      <blz:getBank>\r\n" +
							"         <blz:blz>37050198</blz:blz>\r\n" +
							"         <foo />\r\n" +
							"      </blz:getBank>\r\n" +
							"   </soapenv:Body>\r\n" +
							"</soapenv:Envelope>";

			result = postAndAssert(500, endpoint, invalidRequest);
			assertContains(".java:", result);

			AssertUtils.closeConnections();
			AssertUtils.setupHTTPAuthentication("localhost", 9000, "admin", "membrane");

			pxu.updateWith(
					"<spring:beans xmlns=\"http://membrane-soa.org/proxies/1/\"\r\n" +
							"	xmlns:spring=\"http://www.springframework.org/schema/beans\"\r\n" +
							"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
							"	xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r\n" +
							"					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd\">\r\n" +
							"\r\n" +
							"	<router>\r\n" +
							"	\r\n" +
							"	<soapProxy port=\"2000\" wsdl=\"http://www.thomas-bayer.com/axis2/services/BLZService?wsdl\">\r\n" +
							"		<path>/MyBLZService</path>\r\n" +
							"		<soapStackTraceFilter/>\r\n" +
							"	</soapProxy>\r\n" +
							"	\r\n" +
							"	<serviceProxy port=\"9000\">\r\n" +
							"		<basicAuthentication>\r\n" +
							"			<user name=\"admin\" password=\"membrane\" />\r\n" +
							"		</basicAuthentication>	\r\n" +
							"		<adminConsole />\r\n" +
							"	</serviceProxy>\r\n" +
							"	\r\n" +
							"	</router>\r\n" +
							"</spring:beans>", sl);

			result = postAndAssert(500, endpoint, invalidRequest);
			assertContainsNot(".java:", result);

			AssertUtils.closeConnections();
			AssertUtils.setupHTTPAuthentication("localhost", 9000, "admin", "membrane");

			pxu.updateWith(
					"<spring:beans xmlns=\"http://membrane-soa.org/proxies/1/\"\r\n" +
							"	xmlns:spring=\"http://www.springframework.org/schema/beans\"\r\n" +
							"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
							"	xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r\n" +
							"					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd\">\r\n" +
							"\r\n" +
							"	<router>\r\n" +
							"	\r\n" +
							"	<soapProxy port=\"2000\" wsdl=\"http://www.thomas-bayer.com/axis2/services/BLZService?wsdl\">\r\n" +
							"		<path>/MyBLZService</path>\r\n" +
							"		<soapStackTraceFilter/>\r\n" +
							"		<validator/>\r\n" +
							"	</soapProxy>\r\n" +
							"	\r\n" +
							"	<serviceProxy port=\"9000\">\r\n" +
							"		<basicAuthentication>\r\n" +
							"			<user name=\"admin\" password=\"membrane\" />\r\n" +
							"		</basicAuthentication>	\r\n" +
							"		<adminConsole />\r\n" +
							"	</serviceProxy>\r\n" +
							"	\r\n" +
							"	</router>\r\n" +
							"</spring:beans>", sl);

			assertContains("Validation failed", postAndAssert(400, endpoint, invalidRequest));

			assertContains("1 of 1 messages have been invalid", getAndAssert200("http://localhost:9000/admin/service-proxy/show?name=BLZService%3A2000"));

			assertContains("Target Namespace", getAndAssert200(endpoint));

			assertContains("blz&gt;?XXX?", getAndAssert200(endpoint + "/operation/BLZServiceSOAP11Binding/BLZServicePortType/getBank"));

			AssertUtils.closeConnections();

			pxu.updateWith(
					"<spring:beans xmlns=\"http://membrane-soa.org/proxies/1/\"\r\n" +
							"	xmlns:spring=\"http://www.springframework.org/schema/beans\"\r\n" +
							"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
							"	xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r\n" +
							"					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd\">\r\n" +
							"\r\n" +
							"	<router>\r\n" +
							"	\r\n" +
							"	<soapProxy port=\"2000\" wsdl=\"http://www.thomas-bayer.com/axis2/services/BLZService?wsdl\">\r\n" +
							"		<path>/MyBLZService</path>\r\n" +
							"		<soapStackTraceFilter/>\r\n" +
							"		<validator/>\r\n" +
							"	</soapProxy>\r\n" +
							"	\r\n" +
							"	<serviceProxy port=\"9000\">\r\n" +
							"		<basicAuthentication>\r\n" +
							"			<user name=\"admin\" password=\"membrane\" />\r\n" +
							"		</basicAuthentication>	\r\n" +
							"		<adminConsole />\r\n" +
							"	</serviceProxy>\r\n" +
							"	\r\n" +
							"	<serviceProxy port=\"2000\">\r\n" +
							"		<index />\r\n" +
							"	</serviceProxy>\r\n" +
							"	\r\n" +
							"	</router>\r\n" +
							"</spring:beans>", sl);

			result = getAndAssert200("http://localhost:2000");
			assertContains("/MyBLZService", result);

		} finally {
			sl.killScript();
		}
	}

}
