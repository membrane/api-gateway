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

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.ProxiesXmlUtil;
import com.predic8.membrane.test.AssertUtils;

public class QuickstartRESTTest extends DistributionExtractingTestcase {

	@Test
	public void doit() throws IOException, InterruptedException {
		File baseDir = getExampleDir("quickstart-rest");
		Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
		try {
			String result = getAndAssert200("http://localhost:2000/restnames/name.groovy?name=Pia");
			assertContains("Italy", result);
			AssertUtils.closeConnections();

			new ProxiesXmlUtil(new File(baseDir, "proxies.xml")).updateWith(
					"<spring:beans xmlns=\"http://membrane-soa.org/proxies/1/\"\r\n" + 
					"	xmlns:spring=\"http://www.springframework.org/schema/beans\"\r\n" + 
					"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" + 
					"	xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd\r\n" + 
					"					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd\">\r\n" + 
					"\r\n" + 
					"	<router>\r\n" +
					"\r\n" + 
					"       <serviceProxy name=\"names\" port=\"2000\">\r\n" + 
					"         <path isRegExp=\"true\">/(rest)?names.*</path>\r\n" + 
					"         <rewriter>\r\n" + 
					"           <map from=\"/names/(.*)\" to=\"/restnames/name\\.groovy\\?name=$1\" />\r\n" + 
					"         </rewriter>\r\n" + 
					"         <statisticsCSV file=\"log.csv\" />\r\n" + 
					"         <response>\r\n" + 
					"           <regExReplacer regex=\"\\s*,\\s*&lt;\" replace=\"&lt;\" />\r\n" + 
					"           <transform xslt=\"restnames.xsl\" />\r\n" + 
					"         </response>\r\n" + 
					"         <target host=\"thomas-bayer.com\" port=\"80\" />\r\n" + 
					"       </serviceProxy>\r\n" + 
					"     \r\n" + 
					"       <serviceProxy name=\"Console\" port=\"9000\">\r\n" + 
					"         <basicAuthentication>\r\n" + 
					"           <user name=\"alice\" password=\"membrane\" />\r\n" + 
					"         </basicAuthentication>			\r\n" + 
					"         <adminConsole />\r\n" + 
					"       </serviceProxy>	\r\n" + 
					"     </router>\r\n" +
					"</spring:beans>", sl);
			
			result = getAndAssert200("http://localhost:2000/names/Pia");
			assertContains("Italy, Spain", result);
			assertContainsNot(",<", result);
			
			String csvLog = FileUtils.readFileToString(new File(baseDir, "log.csv"));
			assertContains("Pia", csvLog);
			
			AssertUtils.setupHTTPAuthentication("localhost", 9000, "alice", "membrane");
			result = getAndAssert200("http://localhost:9000/admin/");
			assertContains("ServiceProxies", result);
		} finally {
			sl.killScript();
		}
	}

}
