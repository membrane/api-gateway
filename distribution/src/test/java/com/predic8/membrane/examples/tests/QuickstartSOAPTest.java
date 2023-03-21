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

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

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
					"""
							<spring:beans xmlns="http://membrane-soa.org/proxies/1/"\r
								xmlns:spring="http://www.springframework.org/schema/beans"\r
								xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\r
								xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r
												    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">\r
							\r
								<router>\r
								\r
								<soapProxy port="2000" wsdl="http://www.thomas-bayer.com/axis2/services/BLZService?wsdl">\r
									<path>/MyBLZService</path>\r
								</soapProxy>\r
								\r
								<serviceProxy port="9000">\r
									<basicAuthentication>\r
										<user name="admin" password="membrane" />\r
									</basicAuthentication>	\r
									<adminConsole />\r
								</serviceProxy>\r
								\r
								</router>\r
							</spring:beans>""", sl);

			String endpoint = "http://localhost:2000/MyBLZService";
			String result = getAndAssert200(endpoint + "?wsdl");
			assertContains("wsdl:documentation", result);
			assertContains("localhost:2000/MyBLZService", result);  // assert that rewriting did take place

			result = AssertUtils.postAndAssert200(endpoint,
					"""
							<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:blz="http://thomas-bayer.com/blz/">\r
							   <soapenv:Header/>\r
							   <soapenv:Body>\r
							      <blz:getBank>\r
							         <blz:blz>37050198</blz:blz>\r
							      </blz:getBank>\r
							   </soapenv:Body>\r
							</soapenv:Envelope>""");
			assertContains("Sparkasse", result);

			AssertUtils.setupHTTPAuthentication("localhost", 9000, "admin", "membrane");
			result = getAndAssert200("http://localhost:9000/admin/");
			assertTrue(result.contains("BLZService"));

			String invalidRequest =
					"""
							<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:blz="http://thomas-bayer.com/blz/">\r
							   <soapenv:Header/>\r
							   <soapenv:Body>\r
							      <blz:getBank>\r
							         <blz:blz>37050198</blz:blz>\r
							         <foo />\r
							      </blz:getBank>\r
							   </soapenv:Body>\r
							</soapenv:Envelope>""";

			result = postAndAssert(500, endpoint, invalidRequest);
			assertContains(".java:", result);

			AssertUtils.closeConnections();
			AssertUtils.setupHTTPAuthentication("localhost", 9000, "admin", "membrane");

			pxu.updateWith(
					"""
							<spring:beans xmlns="http://membrane-soa.org/proxies/1/"\r
								xmlns:spring="http://www.springframework.org/schema/beans"\r
								xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\r
								xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r
												    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">\r
							\r
								<router>\r
								\r
								<soapProxy port="2000" wsdl="http://www.thomas-bayer.com/axis2/services/BLZService?wsdl">\r
									<path>/MyBLZService</path>\r
									<soapStackTraceFilter/>\r
								</soapProxy>\r
								\r
								<serviceProxy port="9000">\r
									<basicAuthentication>\r
										<user name="admin" password="membrane" />\r
									</basicAuthentication>	\r
									<adminConsole />\r
								</serviceProxy>\r
								\r
								</router>\r
							</spring:beans>""", sl);

			result = postAndAssert(500, endpoint, invalidRequest);
			assertContainsNot(".java:", result);

			AssertUtils.closeConnections();
			AssertUtils.setupHTTPAuthentication("localhost", 9000, "admin", "membrane");

			pxu.updateWith(
					"""
							<spring:beans xmlns="http://membrane-soa.org/proxies/1/"\r
								xmlns:spring="http://www.springframework.org/schema/beans"\r
								xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\r
								xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r
												    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">\r
							\r
								<router>\r
								\r
								<soapProxy port="2000" wsdl="http://www.thomas-bayer.com/axis2/services/BLZService?wsdl">\r
									<path>/MyBLZService</path>\r
									<soapStackTraceFilter/>\r
									<validator/>\r
								</soapProxy>\r
								\r
								<serviceProxy port="9000">\r
									<basicAuthentication>\r
										<user name="admin" password="membrane" />\r
									</basicAuthentication>	\r
									<adminConsole />\r
								</serviceProxy>\r
								\r
								</router>\r
							</spring:beans>""", sl);

			assertContains("Validation failed", postAndAssert(400, endpoint, invalidRequest));

			assertContains("1 of 1 messages have been invalid", getAndAssert200("http://localhost:9000/admin/service-proxy/show?name=BLZService%3A2000"));

			assertContains("Target Namespace", getAndAssert200(endpoint));

			assertContains("blz&gt;?XXX?", getAndAssert200(endpoint + "/operation/BLZServiceSOAP11Binding/BLZServicePortType/getBank"));

			AssertUtils.closeConnections();

			pxu.updateWith(
					"""
							<spring:beans xmlns="http://membrane-soa.org/proxies/1/"\r
								xmlns:spring="http://www.springframework.org/schema/beans"\r
								xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\r
								xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r
												    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">\r
							\r
								<router>\r
								\r
								<soapProxy port="2000" wsdl="http://www.thomas-bayer.com/axis2/services/BLZService?wsdl">\r
									<path>/MyBLZService</path>\r
									<soapStackTraceFilter/>\r
									<validator/>\r
								</soapProxy>\r
								\r
								<serviceProxy port="9000">\r
									<basicAuthentication>\r
										<user name="admin" password="membrane" />\r
									</basicAuthentication>	\r
									<adminConsole />\r
								</serviceProxy>\r
								\r
								<serviceProxy port="2000">\r
									<index />\r
								</serviceProxy>\r
								\r
								</router>\r
							</spring:beans>""", sl);

			result = getAndAssert200("http://localhost:2000");
			assertContains("/MyBLZService", result);

		} finally {
			sl.killScript();
		}
	}

}
