package com.predic8.membrane.examples.tests;

import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.test.AssertUtils;

public class RateLimiterTest extends DistributionExtractingTestcase {
	@Test
	public void test() throws IOException, InterruptedException {
		Process2 sl = new Process2.Builder().in(getExampleDir("rateLimiter")).script("service-proxy").waitForMembrane()
				.start();
		try {
			AssertUtils.getAndAssert200("http://localhost:2000/");
			AssertUtils.getAndAssert200("http://localhost:2000/");
			AssertUtils.getAndAssert200("http://localhost:2000/");
			AssertUtils.getAndAssert(429, "http://localhost:2000/");

		} finally {
			sl.killScript();
		}
	}

}
