package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.assertContains;
import static com.predic8.membrane.examples.AssertUtils.assertContainsNot;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.parboiled.common.FileUtils;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.HttpClientUtils;
import com.predic8.membrane.examples.ProxiesXmlUtil;
import com.predic8.membrane.examples.ScriptLauncher;

public class QuickstartRESTTest extends DistributionExtractingTestcase {

	@Test
	public void doit() throws IOException, InterruptedException {
		File baseDir = getExampleDir("quickstart-rest");
		ScriptLauncher sl = new ScriptLauncher(baseDir).startScript("router");
		try {

			HttpClient hc = new DefaultHttpClient();
			HttpResponse res = hc.execute(new HttpGet("http://localhost:2000/restnames/name.groovy?name=Pia"));
			assertEquals(200, res.getStatusLine().getStatusCode());
			assertContains("Italy", EntityUtils.toString(res.getEntity()));

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
			
			res = hc.execute(new HttpGet("http://localhost:2000/names/Pia"));
			assertEquals(200, res.getStatusLine().getStatusCode());
			String result = EntityUtils.toString(res.getEntity());
			assertContains("Italy, Spain", result);
			assertContainsNot(",<", result);
			
			String csvLog = FileUtils.readAllText(new File(baseDir, "log.csv"));
			assertContains("Pia", csvLog);
			
			hc = HttpClientUtils.getAuthenticatingHttpClient("localhost", 9000, "alice", "membrane");
			res = hc.execute(new HttpGet("http://localhost:9000/admin/"));
			assertEquals(200, res.getStatusLine().getStatusCode());
			assertContains("ServiceProxies", EntityUtils.toString(res.getEntity()));

		} finally {
			sl.killScript();
		}
	}

}
