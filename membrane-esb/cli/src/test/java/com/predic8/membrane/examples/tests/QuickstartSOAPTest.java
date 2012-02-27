package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.assertContains;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ProxiesXmlUtil;
import com.predic8.membrane.examples.ScriptLauncher;

public class QuickstartSOAPTest extends DistributionExtractingTestcase {

	@Test
	public void doit() throws IOException, InterruptedException {
		File baseDir = getExampleDir("quickstart-soap");
		ScriptLauncher sl = new ScriptLauncher(baseDir).startScript("router");
		try {
			String result = getAndAssert200("http://localhost:2000/axis2/services/BLZService?wsdl");
			assertContains("wsdl:documentation", result);
			assertContains("www.thomas-bayer.com:80", result);  // assert that rewriting did not take place

			ProxiesXmlUtil pxu = new ProxiesXmlUtil(new File(baseDir, "quickstart-soap.proxies.xml"));
			pxu.updateWith(
					"<proxies>\r\n" + 
					"  <serviceProxy name=\"BLZ\" port=\"2000\">\r\n" + 
					"    <wsdlRewriter />\r\n" + 
					"    <target host=\"www.thomas-bayer.com\" port=\"80\" />\r\n" + 
					"  </serviceProxy>\r\n" + 
					"\r\n" + 
					"  <serviceProxy name=\"Console\" port=\"9000\">\r\n" + 
					"    <adminConsole />\r\n" + 
					"  </serviceProxy>	\r\n" + 
					"</proxies>", sl);

			result = getAndAssert200("http://localhost:2000/axis2/services/BLZService?wsdl");
			assertContains("localhost:2000", result); // assert that rewriting took place

			pxu.updateWith(
					"<proxies>\r\n" + 
					"  <serviceProxy name=\"BLZ\" port=\"2000\">\r\n" + 
					"    <rest2Soap>\r\n" + 
					"      <mapping regex=\"/bank/.*\" soapAction=\"\"\r\n" + 
					"        soapURI=\"/axis2/services/BLZService\" \r\n" + 
					"        requestXSLT=\"get2soap.xsl\"\r\n" + 
					"        responseXSLT=\"strip-env.xsl\" />\r\n" + 
					"    </rest2Soap>\r\n" + 
					"    <validator wsdl=\"http://www.thomas-bayer.com/axis2/services/BLZService?wsdl\" />\r\n" +
					"    <wsdlRewriter />\r\n" + 
					"    <target host=\"www.thomas-bayer.com\" port=\"80\" />\r\n" + 
					"  </serviceProxy>\r\n" + 
					"\r\n" + 
					"  <serviceProxy name=\"Console\" port=\"9000\">\r\n" + 
					"    <adminConsole />\r\n" + 
					"  </serviceProxy>	\r\n" + 
					"</proxies>", sl);
			
			result = getAndAssert200("http://localhost:2000/bank/37050198");
			assertContains("plz>50667", result);
			
			result = AssertUtils.postAndAssert200("http://localhost:2000/axis2/services/BLZService", 
					"<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" \r\n" + 
					"      xmlns:blz=\"http://thomas-bayer.com/blz/\">\r\n" + 
					"   <soapenv:Header/>\r\n" + 
					"   <soapenv:Body>\r\n" + 
					"      <blz:getBank>\r\n" + 
					"   <blz:blz>37050198</blz:blz>\r\n" + 
					"      </blz:getBank>\r\n" + 
					"   </soapenv:Body>\r\n" + 
					"</soapenv:Envelope>");
			assertContains("plz>50667", result);
			
			result = AssertUtils.postAndAssert(400, "http://localhost:2000/axis2/services/BLZService", 
					"<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" \r\n" + 
					"		  xmlns:blz=\"http://thomas-bayer.com/blz/\">\r\n" + 
					"   <soapenv:Header/>\r\n" + 
					"   <soapenv:Body>\r\n" + 
					"      <blz:getBank>\r\n" + 
					"	 <blz:blz>37050198</blz:blz>\r\n" + 
					"	    <foo/>\r\n" + // <- invalid message 
					"      </blz:getBank>\r\n" + 
					"   </soapenv:Body>\r\n" + 
					"</soapenv:Envelope>");
			assertContains("failed", result);
		} finally {
			sl.killScript();
		}
	}

}
