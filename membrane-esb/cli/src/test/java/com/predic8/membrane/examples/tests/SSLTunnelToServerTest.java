package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class SSLTunnelToServerTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
		File baseDir = getExampleDir("ssl-tunnel-to-server");
		
		AssertUtils.replaceInFile(new File(baseDir, "ssl-tunnel-to-server.proxies.xml"), "8080", "3023");
		
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			AssertUtils.assertContains("<svn", getAndAssert200("http://localhost:3023/svn/membrane/monitor/"));
		} finally {
			sl.killScript();
		}
	}


}
