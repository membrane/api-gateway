package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoadBalancerStaticTest extends DistributionExtractingTestcase {

	private Pattern nodePattern = Pattern.compile("Mock Node (\\d+)");
	
	@Test
	public void test() throws IOException, InterruptedException {
		Process2 sl = new Process2.Builder().in(getExampleDir("loadbalancer-static")).script("router").waitForMembrane().start();
		try {
			for (int i = 0; i < 7; i++) {
				Matcher m = nodePattern.matcher(getAndAssert200("http://localhost:8080/service"));
				Assert.assertTrue(m.find());
				int respondingNode = Integer.parseInt(m.group(1));
				Assert.assertEquals(i % 3 + 1, respondingNode);
			}
		} finally {
			sl.killScript();
		}
	}

}
