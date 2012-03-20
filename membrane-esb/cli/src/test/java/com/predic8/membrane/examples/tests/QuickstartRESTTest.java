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

import static com.predic8.membrane.examples.AssertUtils.assertContains;
import static com.predic8.membrane.examples.AssertUtils.assertContainsNot;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.parboiled.common.FileUtils;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ProxiesXmlUtil;
import com.predic8.membrane.examples.Process2;

public class QuickstartRESTTest extends DistributionExtractingTestcase {

	@Test
	public void doit() throws IOException, InterruptedException {
		File baseDir = getExampleDir("quickstart-rest");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			String result = getAndAssert200("http://localhost:2000/restnames/name.groovy?name=Pia");
			assertContains("Italy", result);

			new ProxiesXmlUtil(new File(baseDir, "quickstart-rest.proxies.xml")).updateWith(
					"     <proxies>\r\n" + 
					"       <serviceProxy name=\"names\" port=\"2000\">\r\n" + 
					"         <request>\r\n" + 
					"           <rewriter>\r\n" + 
					"             <map from=\"/names/(.*)\" to=\"/restnames/name\\.groovy\\?name=$1\" />\r\n" + 
					"           </rewriter>\r\n" + 
					"         </request>\r\n" + 
					"         <statisticsCSV file=\"log.csv\" />\r\n" + 
					"         <response>\r\n" + 
					"           <regExReplacer regex=\"\\s*,\\s*&lt;\" replace=\"&lt;\" />\r\n" + 
					"           <transform xslt=\"restnames.xsl\" />\r\n" + 
					"         </response>\r\n" + 
					"         <path isRegExp=\"true\">/(rest)?names.*</path>\r\n" + 
					"         <target host=\"thomas-bayer.com\" port=\"80\" />\r\n" + 
					"       </serviceProxy>\r\n" + 
					"     \r\n" + 
					"       <serviceProxy name=\"Console\" port=\"9000\">\r\n" + 
					"         <basicAuthentication>\r\n" + 
					"           <user name=\"alice\" password=\"membrane\" />\r\n" + 
					"         </basicAuthentication>			\r\n" + 
					"         <adminConsole />\r\n" + 
					"       </serviceProxy>	\r\n" + 
					"     </proxies>", sl);
			
			result = getAndAssert200("http://localhost:2000/names/Pia");
			assertContains("Italy, Spain", result);
			assertContainsNot(",<", result);
			
			String csvLog = FileUtils.readAllText(new File(baseDir, "log.csv"));
			assertContains("Pia", csvLog);
			
			AssertUtils.setupHTTPAuthentication("localhost", 9000, "alice", "membrane");
			result = getAndAssert200("http://localhost:9000/admin/");
			assertContains("ServiceProxies", result);
		} finally {
			sl.killScript();
		}
	}

}
