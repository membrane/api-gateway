package com.predic8.membrane.examples.tests;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoadBalancerStaticTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File base = getExampleDir("loadbalancer-static");
		
		AssertUtils.replaceInFile(new File(base, "lb-static.proxies.xml"), "8080", "3023");
		
		Process2 sl = new Process2.Builder().in(base).script("router").waitForMembrane().start();
		try {
			for (int i = 0; i < 7; i++)
				Assert.assertEquals(
						i % 3 + 1, 
						LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
		} finally {
			sl.killScript();
		}
	}

}
