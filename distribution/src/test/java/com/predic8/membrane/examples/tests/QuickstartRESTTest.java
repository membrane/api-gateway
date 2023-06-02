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

import static com.predic8.membrane.test.AssertUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.test.AssertUtils;

public class QuickstartRESTTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "quickstart-rest";
	}

	@Test
	public void doit() throws IOException, InterruptedException, TimeoutException {
		Process2 sl = startServiceProxyScript();
		try {
			String result = getAndAssert200("http://localhost:2000/restnames/name.groovy?name=Pia");
			assertContains("Italy", result);
			AssertUtils.closeConnections();

			new ProxiesXmlUtil(new File(baseDir, "proxies.xml")).updateWith(
					"""
							<spring:beans xmlns="http://membrane-soa.org/proxies/1/"\r
								xmlns:spring="http://www.springframework.org/schema/beans"\r
								xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\r
								xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd\r
												    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">\r
							\r
								<router>\r
							\r
							       <serviceProxy name="names" port="2000">\r
							         <path isRegExp="true">/(rest)?names.*</path>\r
							         <rewriter>\r
							           <map from="/names/(.*)" to="/restnames/name\\.groovy\\?name=$1" />\r
							         </rewriter>\r
							         <statisticsCSV file="log.csv" />\r
							         <response>\r
							           <regExReplacer regex="\\s*,\\s*&lt;" replace="&lt;" />\r
							           <transform xslt="restnames.xsl" />\r
							         </response>\r
							         <target host="thomas-bayer.com" port="80" />\r
							       </serviceProxy>\r
							     \r
							       <serviceProxy name="Console" port="9000">\r
							         <basicAuthentication>\r
							           <user name="alice" password="membrane" />\r
							         </basicAuthentication>			\r
							         <adminConsole />\r
							       </serviceProxy>	\r
							     </router>\r
							</spring:beans>""", sl);

			result = getAndAssert200("http://localhost:2000/names/Pia");
			assertContains("Italy, Spain", result);
			assertContainsNot(",<", result);

			assertContains("Pia", readFileFromBaseDir("log.csv"));

			setupHTTPAuthentication("localhost", 9000, "alice", "membrane");
			assertContains("ServiceProxies", getAndAssert200("http://localhost:9000/admin/"));
		} finally {
			sl.killScript();
		}
	}
}
