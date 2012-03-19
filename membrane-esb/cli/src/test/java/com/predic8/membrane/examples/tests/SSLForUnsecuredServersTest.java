package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.assertContains;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class SSLForUnsecuredServersTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
		File baseDir = getExampleDir("ssl-for-unsecured-servers");
		
		AssertUtils.replaceInFile(new File(baseDir, "unsecured-server.proxies.xml"), "443", "3023");
		
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			AssertUtils.trustAnyHTTPSServer(3023);
			
			assertContains("wsdl:documentation", getAndAssert200("https://localhost:3023/axis2/services/BLZService?wsdl"));
		} finally {
			sl.killScript();
		}
	}


}
