package com.predic8.membrane.examples.tests;

import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.ConsoleLogger;
import com.predic8.membrane.test.AssertUtils;


public class RateLimiterTest extends DistributionExtractingTestcase {
	@Test
	public void test() throws IOException, InterruptedException {
		Process2 sl = new Process2.Builder().in(getExampleDir("rateLimiter")).script("service-proxy").withWatcher(new ConsoleLogger()).waitForMembrane().start();
		try {
			String response = AssertUtils.getAndAssert200("http://localhost:2000/");
			System.out.println(response);
			
		} finally {
			sl.killScript();
		}
	}

}
